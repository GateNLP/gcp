/*
 *  JSONStreamingInputHandler.java
 *  Copyright (c) 2007-2014, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: JSONStreamingInputHandler.java 18660 2015-05-01 15:01:14Z ian_roberts $ 
 */
package gate.cloud.io.json;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_COMPRESSION;
import static gate.cloud.io.IOConstants.PARAM_ID_POINTER;
import static gate.cloud.io.IOConstants.PARAM_ID_TEMPLATE;
import static gate.cloud.io.IOConstants.PARAM_MIME_TYPE;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_GZIP;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.GateConstants;
import gate.Utils;
import gate.cloud.batch.Batch;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.DocumentData;
import gate.cloud.io.IOConstants;
import gate.cloud.io.StreamingInputHandler;
import gate.util.GateException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Streaming-mode input handler that reads from a file containing either
 * a top-level JSON array containing a number of objects, or a sequence
 * of top-level JSON objects concatenated together. This is the typical
 * format returned by social media streams from Twitter or DataSift.
 * Each JSON object will be treated as a GATE document, with the
 * document ID taken from a property or properties in the JSON (the path
 * to the ID property is a configuration option). Objects for which the
 * ID cannot be found or constructed will be ignored.
 * </p>
 * <p>
 * The input file may be compressed. The following values of the
 * "compression" option can be handled natively in Java by Apache
 * commons-compress: "gz" (or "gzip"), "bzip2", "xz", "z" (the Unix
 * <code>compress</code> format), "pack200", "lzma", "snappy-raw",
 * "snappy-framed", "deflate". The value "any" will attempt to
 * auto-detect the compression format, falling back on no compression if
 * auto-detection fails. Any other value of "compression" will be
 * treated as a command line to a program that expects the compressed
 * data on its standard input and will produce uncompressed output on
 * its standard out. The command will be split into words at whitespace,
 * so embedded whitespace within a single word is not permitted. For
 * example, to handle JSON files compressed in LZO format use
 * <code>compression="lzop -dc"</code>.
 * </p>
 * <p>
 * Example for Twitter streaming API:
 * </p>
 * 
 * <pre>
 * &lt;input class="gate.cloud.io.json.JSONStreamingInputHandler"
 *        srcFile="tweets.lzo"
 *        compression="lzop -dc"
 *        mimeType="text/x-json-twitter"
 *        idPointer="/id_str" />
 * </pre>
 * 
 * <p>
 * Example for DataSift:
 * </p>
 * 
 * <pre>
 * &lt;input class="gate.cloud.io.json.JSONStreamingInputHandler"
 *        srcFile="interactions.gz"
 *        compression="gz"
 *        mimeType="text/x-json-datasift"
 *        idPointer="/interaction/id" />
 * </pre>
 * 
 * @author Ian Roberts
 * 
 */
public class JSONStreamingInputHandler implements StreamingInputHandler {

  private static Logger logger = LoggerFactory
          .getLogger(JSONStreamingInputHandler.class);

  public DocumentData getInputDocument(DocumentID id) throws IOException,
          GateException {
    throw new UnsupportedOperationException(
            "JSONStreamingInputHandler can only operate in streaming mode");
  }

  /**
   * Base directory of the batch.
   */
  protected File batchDir;

  /**
   * The source file from which the JSON objects will be streamed.
   */
  protected File srcFile;

  /**
   * Compression applied to the input file. This can be
   * {@link IOConstants#VALUE_COMPRESSION_GZIP} in which case the file
   * will be unpacked using Java's native GZIP support. Any other value
   * is assumed to be a command line to an external command that can
   * accept an additional parameter giving the path to the file and
   * produce the uncompressed data on its standard output, e.g.
   * "lzop -dc" for .lzo compression.
   */
  protected String compression;

  /**
   * The mime type used when loading documents.
   */
  protected String mimeType;

  /**
   * Function that extracts a document ID from a given JSON node.  The
   * function is derived from the idPointer/idTemplate specified in
   * configuration.
   */
  protected Function<JsonNode, String> idExtractor;

  /**
   * Document IDs that are already complete after a previous run of this
   * batch.
   */
  protected Set<String> completedDocuments;

  /**
   * External decompression process, if applicable.
   */
  protected Process decompressProcess = null;

  protected ObjectMapper objectMapper;

  protected JsonParser jsonParser;

  protected MappingIterator<JsonNode> docIterator;

  public void config(Map<String, String> configData) throws IOException,
          GateException {
    // srcFile
    String srcFileStr = configData.get(PARAM_SOURCE_FILE_LOCATION);
    if(srcFileStr == null) {
      throw new IllegalArgumentException("Parameter "
              + PARAM_SOURCE_FILE_LOCATION + " is required");
    } else {
      String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
      if(batchFileStr != null) {
        batchDir = new File(batchFileStr).getParentFile();
      }
      srcFile = new File(srcFileStr);
      if(!srcFile.isAbsolute()) {
        srcFile = new File(batchDir, srcFileStr);
      }
      if(!srcFile.exists()) {
        throw new IllegalArgumentException("File \"" + srcFile
                + "\", provided as value for required parameter \""
                + PARAM_SOURCE_FILE_LOCATION + "\", does not exist!");
      }
      if(!srcFile.isFile()) {
        throw new IllegalArgumentException("File \"" + srcFile
                + "\", provided as value for required parameter \""
                + PARAM_SOURCE_FILE_LOCATION + "\", is not a file!");
      }
    }

    // idPointer
    String idPointerStr = configData.get(PARAM_ID_POINTER);
    String idTemplateStr = configData.get(PARAM_ID_TEMPLATE);
    if(idPointerStr == null && idTemplateStr == null) {
      throw new IllegalArgumentException("One of " + PARAM_ID_POINTER
              + " or " + PARAM_ID_TEMPLATE + " is required");
    }
    if(idPointerStr != null) {
      JsonPointer idPointer = JsonPointer.compile(idPointerStr);
      idExtractor = (node) -> node.at(idPointer).asText();
    } else {
      idExtractor = compileIdTemplate(idTemplateStr);
    }

    // compression
    compression = configData.get(PARAM_COMPRESSION);
    // mime type
    mimeType = configData.get(PARAM_MIME_TYPE);
  }

  /**
   * Compile an idTemplate string into a function that takes a JsonNode representing
   * a document and returns a String representing its identifier.  The template string
   * is expected to be a series of alternatives separated by pipe characters (|), each
   * alternative is a string containing one or more brace expressions {...} which
   * will be treated as JSON Pointer expressions and replaced with the relevant value
   * extracted from that position in the document JSON.  The alternatives are tried one
   * by one from left to right, the first alternative for which all the pointer
   * expressions resolve to non-null string/number/boolean values will be used to
   * generate the final ID.  If no alternative can be fully resolved, the function
   * returns a null ID, which will cause the document to be skipped.
   *
   * @param template the template string
   * @return an extractor function
   * @throws IllegalArgumentException if any of the brace expressions fails to parse
   * as a JSON Pointer.
   */
  protected static Function<JsonNode, String> compileIdTemplate(String template) {
    Pattern placeholderPattern = Pattern.compile("\\{([^}]+)}");
    // Start with an initial function that always returns null, so we don't
    // have to make a special case for the last alternative in the logic below
    Function<JsonNode, String> fn = (node) -> null;
    String[] alternatives = template.split(Pattern.quote("|"));
    // process the segments from right to left, so the function
    // for each one can chain to the next
    for(int j = alternatives.length - 1; j >= 0; j--) {
      String tmpl = alternatives[j];
      final List<String> fixedStrings = new ArrayList<>();
      final List<JsonPointer> pointers = new ArrayList<>();
      Matcher m = placeholderPattern.matcher(tmpl);
      // sanity check on the template
      if(!m.find()) {
        throw new IllegalArgumentException("idTemplate must include at least one pointer in every alternative");
      }
      // now reset and start the actual compilation
      m.reset();
      int lastIndex = 0;
      while(m.find()) {
        fixedStrings.add(tmpl.substring(lastIndex, m.start()));
        // unescape any } and | inside the {...} to get the actual pointer expression
        String ptr = m.group(1).replace("~3", "|")
                .replace("~2", "}");
        pointers.add(JsonPointer.compile(ptr));
        lastIndex = m.end();
      }
      final String tail = tmpl.substring(lastIndex);

      final Function<JsonNode, String> nextFn = fn;
      fn = (node) -> {
        StringBuilder str = new StringBuilder();
        for(int i = 0; i < pointers.size(); i++) {
          str.append(fixedStrings.get(i));
          JsonNode target = node.at(pointers.get(i));
          if(!target.isValueNode() || target.isNull()) {
            // pointer either did not find anything at all, or it found null, an
            // object or an array (which cannot be sensibly converted to string).
            // Give up and try the next alternative, if any.
            return nextFn.apply(node);
          }
          str.append(target.asText());
        }
        str.append(tail);
        return str.toString();
      };
    }

    return fn;
  }

  public void startBatch(Batch b) {
    completedDocuments = b.getCompletedDocuments();
    if(completedDocuments != null && completedDocuments.size() > 0) {
      logger.info("Restarting failed batch - {} documents already processed",
              completedDocuments.size());
    }
  }

  public void init() throws IOException, GateException {
    InputStream inputStream = null;
    if(compression == null) {
      inputStream = new FileInputStream(srcFile);
    } else if("any".equals(compression)) {
      inputStream = new BufferedInputStream(new FileInputStream(srcFile));
      try {
        inputStream =
                new CompressorStreamFactory()
                        .createCompressorInputStream(inputStream);
      } catch(CompressorException e) {
        if(e.getCause() != null) {
          if(e.getCause() instanceof IOException) {
            throw (IOException)e.getCause();
          } else {
            throw new GateException(e.getCause());
          }
        } else {
          // unrecognised signature, assume uncompressed
          logger.info("Failed to detect compression format, assuming no compression");
        }
      }
    } else {
      if(VALUE_COMPRESSION_GZIP.equals(compression)) {
        compression = CompressorStreamFactory.GZIP;
      }
      inputStream = new BufferedInputStream(new FileInputStream(srcFile));
      try {
        inputStream =
                new CompressorStreamFactory()
                        .createCompressorInputStream(compression, inputStream);
      } catch(CompressorException e) {
        if(e.getCause() != null) {
          if(e.getCause() instanceof IOException) {
            throw (IOException)e.getCause();
          } else {
            throw new GateException(e.getCause());
          }
        } else {
          // unrecognised compressor name
          logger.info("Unrecognised compression format, assuming external compressor");
          IOUtils.closeQuietly(inputStream);
          // treat compression value as a command line
          ProcessBuilder pb = new ProcessBuilder(compression.trim().split("\\s+"));
          pb.directory(batchDir);
          pb.redirectError(Redirect.INHERIT);
          pb.redirectOutput(Redirect.PIPE);
          pb.redirectInput(srcFile);
          decompressProcess = pb.start();
          inputStream = decompressProcess.getInputStream();
        }
      }
    }

    objectMapper = new ObjectMapper();
    jsonParser =
            objectMapper.getFactory().createParser(inputStream)
                    .enable(Feature.AUTO_CLOSE_SOURCE);
    // If the first token in the stream is the start of an array ("[")
    // then
    // assume the stream as a whole is an array of objects, one per
    // document.
    // To handle this, simply clear the token - The MappingIterator
    // returned
    // by readValues will cope with the rest in either form.
    if(jsonParser.nextToken() == JsonToken.START_ARRAY) {
      jsonParser.clearCurrentToken();
    }
    docIterator = objectMapper.readValues(jsonParser, JsonNode.class);
  }

  public void close() throws IOException, GateException {
    docIterator.close();
    jsonParser.close();
    if(decompressProcess != null) {
      try {
        decompressProcess.waitFor();
      } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public DocumentData nextDocument() throws IOException, GateException {
    while(docIterator.hasNextValue()) {
      JsonNode json = docIterator.nextValue();
      String id = idExtractor.apply(json);
      if(id == null || "".equals(id)) {
        // can't find an ID, assume this is a "delete" or similar and
        // ignore it
        if(logger.isDebugEnabled()) {
          logger.debug("No ID found in JSON object {} - ignored", json);
        }
      } else if(completedDocuments.contains(id)) {
        // already processed, ignore
      } else {
        DocumentID docId = new DocumentID(id);
        FeatureMap docParams = Factory.newFeatureMap();
        docParams.put(Document.DOCUMENT_STRING_CONTENT_PARAMETER_NAME,
                json.toString());
        if(mimeType != null) {
          docParams.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, mimeType);
        }
        try {
          Document gateDoc =
                  (Document)Factory.createResource("gate.corpora.DocumentImpl",
                          docParams, Utils.featureMap(
                                  GateConstants.THROWEX_FORMAT_PROPERTY_NAME,
                                  Boolean.TRUE), id);
          return new DocumentData(gateDoc, docId);
        } catch(Exception e) {
          logger.warn("Error encountered while parsing object with ID " + id
                  + " - skipped", e);
        }
      }
    }
    return null;
  }

}
