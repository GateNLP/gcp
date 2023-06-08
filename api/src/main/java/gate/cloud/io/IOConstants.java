/*
 *  IOConstants.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: IOConstants.java 19679 2016-10-14 16:40:39Z johann_p $ 
 */
package gate.cloud.io;

/**
 * A class holding a set of useful constants.
 */
public class IOConstants {
  private IOConstants() {
    // class should not be instantiated
  }
  
  /**
   * The mime type for the input files.
   */
  public static final String PARAM_MIME_TYPE = "mimeType";

  /**
   * The location (absolute path) of the file which described the batch, if the
   * batch came from a file.
   */
  public static final String PARAM_BATCH_FILE_LOCATION = "batchFileLocation";
  
  /**
   * The name of the NamingStrategy class used to convert document IDs to file
   * names.
   */
  public static final String PARAM_NAMING_STRATEGY = "namingStrategy";

  /**
   * The top-level directory containing the document files.
   */
  public static final String PARAM_DOCUMENT_ROOT = "dir";

  /**
   * The extension to be used for all file names.
   */
  public static final String PARAM_FILE_EXTENSION = "fileExtension";

  /**
   * If this is true, any given extension is used to replace any existing file extension.
   * If this configuration option is true, then PARAM_FILE_EXTENSION is not empty and the 
   * file name already does have an extension (something following a dot but not including a dot),
   * then the existing extension is replaced with the new extension. 
   */
  public static final String PARAM_REPLACE_EXTENSION = "replaceExtension";

  /**
   * Parameter name for file name prefixes (used e.g. when enumerating files 
   * inside a directory and making them appear under a higer-level top 
   * directory).
   */
  public static final String PARAM_FILE_NAME_PREFIX = "prefix";
  
  /**
   * The encoding to be used for the input files.
   */
  public static final String PARAM_ENCODING = "encoding";

  /**
   * Parameter name for the document feature name used when performing 
   * conditional output in an {@link OutputHandler}. If supplied, the output 
   * handler will only save document for which the named feature has a boolean
   * true value (&quot;true&quot;, &quot;yes&quot;, or &quot;on&quot;).
   */
  public static final String PARAM_CONDITIONAL_SAVE_FEATURE_NAME = 
      "conditionalSaveFeatureName";
  
  /**
   * Configuration parameter specifying whether the input files are compressed.
   * Valid values for this parameter are
   * {@link IOConstants#VALUE_COMPRESSION_NONE}, and
   * {@link IOConstants#VALUE_COMPRESSION_GZIP}.
   */
  public static final String PARAM_COMPRESSION = "compression";

  /**
   * The input files are not compressed.
   */
  public static final String VALUE_COMPRESSION_NONE = "none";

  /**
   * The input files are gzip-compressed (and have the extension &quot;.gz&quot;
   * appended to the normal extension specific to their mime type).
   */
  public static final String VALUE_COMPRESSION_GZIP = "gzip";
  
  /**
   * The files are Snappy-compressed (and have the extension &quot;.snappy&quot;
   * appended to the normal extension specific to their mime type).
   */
  public static final String VALUE_COMPRESSION_SNAPPY = "snappy";

  /**
   * The location of an ARC file.
   */
  @Deprecated
  public static final String PARAM_ARC_FILE_LOCATION = "arcFile";

  /**
   * The location of a source file.
   */
  public static final String PARAM_SOURCE_FILE_LOCATION = "srcFile";

  /**
   * A space-separated list of mime types of interest.
   */
  public static final String PARAM_MIME_TYPES = "mimeTypes";
  
  /**
   * A regular expression matching ARC/WARC file status codes we are
   * interested in.
   */
  public static final String PARAM_INCLUDE_STATUS_CODES = "includeStatusCodes";

  /**
   * A regular expression matching ARC/WARC file status codes we are not
   * interested in.
   */
  public static final String PARAM_EXCLUDE_STATUS_CODES = "excludeStatusCodes";
  
  /**
   * The default encoding that should be used to read records from an
   * ARC or WARC file that don't specify their own encoding in the headers.
   */
  public static final String PARAM_DEFAULT_ENCODING = "defaultEncoding";
  
  /**
   * The location of a zip file.
   */
  @Deprecated
  public static final String PARAM_ZIP_FILE_LOCATION = "zipFile";
  
  /**
   * Ant-style include patterns for an enumerator.
   */
  public static final String PARAM_INCLUDES = "includes";
  
  /**
   * Ant-style exclude patterns for an enumerator.
   */
  public static final String PARAM_EXCLUDES = "excludes";
  
  /**
   * Should Ant-based enumerators use the default Ant exclude patterns?
   */
  public static final String PARAM_DEFAULT_EXCLUDES = "defaultExcludes";
  
  /**
   * File name pattern for naming strategies.
   */
  public static final String PARAM_PATTERN = "pattern";
  
  /**
   * Default encoding for file names in a zip file.  Defaults to the
   * current platform default encoding.
   */
  public static final String PARAM_FILE_NAME_ENCODING = "fileNameEncoding";
  
  /**
   * JSON Pointer expression defining where to find the document ID in a
   * streamed JSON object.
   */
  public static final String PARAM_ID_POINTER = "idPointer";

  /**
   * <p>
   * Template allowing the document ID to be built up from multiple values
   * from a streamed JSON object.  For example
   * <code>idTemplate="{/channel}_{/index}"</code>
   * would generate an ID by taking the top level "channel" and "index"
   * properties from the JSON and combining them with an underscore separator.
   * Multiple alternative templates can be separated by | and will be tried
   * one by one from left to right, the first template for which every
   * placeholder resolves successfully will be used.  So for example
   * <code>idTemplate="{/channel}_{/index}|{/group}_{/index}"</code> would
   * use the channel and index to form the ID if the JSON object has a
   * channel key, or the group and index if it does not have a channel, and
   * skip the document if it has neither channel nor group.
   * </p>
   *
   * <p>
   * Since JSON property names may contain brace or pipe characters, these
   * may be escaped as ~2 (for closing brace "}") and ~3 (for pipe "|"),
   * analagous to the way / and ~ are escaped as ~1 and ~0 in the JSON
   * Pointer language.
   * </p>
   */
  public static final String PARAM_ID_TEMPLATE = "idTemplate";
  
  /**
   * Target size for a single output file from a streaming output handler.
   */
  public static final String PARAM_CHUNK_SIZE = "chunkSize";
  
  /**
   * Parameter indicating that a component should collect or make use
   * of repositioning info if it is available.
   */
  public static final String PARAM_REPOSITIONING_INFO = "repositioningInfo";

  /**
   * XML namespace used for all elements in a batch definition XML file.
   */
  public static final String BATCH_NAMESPACE =
          "http://gate.ac.uk/ns/cloud/batch/1.0";
}
