/*
 *  XMLBatchParser.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: XMLBatchParser.java 18202 2014-07-20 18:55:23Z ian_roberts $ 
 */
package gate.cloud.util;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import gate.CorpusController;
import gate.Gate;
import gate.cloud.batch.AnnotationSetDefinition;
import gate.cloud.batch.Batch;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.DocumentEnumerator;
import gate.cloud.io.InputHandler;
import gate.cloud.io.OutputHandler;
import gate.cloud.io.IOConstants;
import gate.util.GateException;
import gate.util.persistence.PersistenceManager;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.log4j.Logger;

/**
 * A {@link Batch} implementation that reads its data from an XML source.
 */
public class XMLBatchParser{

  private static XMLInputFactory staxInputFactory =
    XMLInputFactory.newInstance();

  /**
   * Log4J logger.
   */
  private static Logger logger = Logger.getLogger(XMLBatchParser.class);
  
  /**
   * @param inputStream
   * @return
   * @throws XMLStreamException
   * @throws FactoryConfigurationError
   * @throws GateException
   *           if the provided XML data cannot be parsed correctly.
   * @throws IOException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws ClassNotFoundException
   */
  public static Batch fromXml(File inputFile) throws XMLStreamException,
          FactoryConfigurationError, GateException, ClassNotFoundException,
          InstantiationException, IllegalAccessException, IOException {
    Batch batch = new Batch();
    // gather the input and output handler specifications but don't
    // attempt to instantiate the handlers until after parsing has
    // finished.  This allows us to load handler classes from plugin
    // JAR files that are loaded along with the GATE application.
    HandlerSpec inputHandlerSpec = null;
    List<HandlerSpec> outputHandlerSpecs =  new ArrayList<HandlerSpec>();
    List<Object> docIDsOrSpecs = null;
    InputStream inputStream =
            new BufferedInputStream(new FileInputStream(inputFile));
    try {
      XMLStreamReader xsr =
              staxInputFactory.createXMLStreamReader(inputFile.toURI().toURL()
                      .toString(), inputStream);
      while(xsr.next() != XMLStreamConstants.START_ELEMENT) {
        // do nothing
      }
      xsr.require(XMLStreamConstants.START_ELEMENT,
              IOConstants.BATCH_NAMESPACE, "batch");
      batch.setBatchId(xsr.getAttributeValue(XMLConstants.NULL_NS_URI, "id"));
      int type = xsr.nextTag();
      while(type == XMLStreamConstants.START_ELEMENT) {
        String elemName = xsr.getLocalName();
        if(elemName.equals("input")) {
          inputHandlerSpec = extractInputHandler(xsr, inputFile);
        } else if(elemName.equals("output")) {
          HandlerSpec outHandler = extractOutputHandler(xsr, inputFile);
          if(outHandler != null) {
            outputHandlerSpecs.add(outHandler);
          }
        } else if(elemName.equals("application")) {
          String appFileStr =
                  xsr.getAttributeValue(XMLConstants.NULL_NS_URI, "file");
          if(appFileStr != null) {
            File appFile = new File(appFileStr);
            if(!appFile.isAbsolute()) {
              appFile = new File(inputFile.getParentFile().getAbsoluteFile(),
                              appFileStr);
            }
            // load the application
            batch.setGateApplication(
                    (CorpusController)PersistenceManager.loadObjectFromFile(
                            appFile));
          } else {
            Location location = xsr.getLocation();
            throw new GateException(
                    "No file attribute provided for the application element "
                            + "at character offset: "
                            + location.getCharacterOffset() + ", line: "
                            + location.getLineNumber() + ", column: "
                            + location.getColumnNumber());
          }
          // close this tag
          xsr.nextTag();
        } else if(elemName.equals("report")) {
          String reportFileStr =
                  xsr.getAttributeValue(XMLConstants.NULL_NS_URI, "file");
          if(reportFileStr != null) {
            File repFile = new File(reportFileStr);
            if(!repFile.isAbsolute()) {
              repFile = new File(inputFile.getParentFile().getAbsoluteFile(),
                              reportFileStr);
            }
            batch.setReportFile(repFile);
          } else {
            Location location = xsr.getLocation();
            throw new GateException(
                    "No file attribute provided for the report element "
                            + "at character offset: "
                            + location.getCharacterOffset() + ", line: "
                            + location.getLineNumber() + ", column: "
                            + location.getColumnNumber());
          }
          // close this tag
          xsr.nextTag();
        } else if(elemName.equals("documents")) {
          docIDsOrSpecs = extractDocumentIDs(xsr, inputFile);
        } else {
          // some other element that we don't understand
          Location place = xsr.getLocation();
          throw new GateException("Unknown element \"" + elemName
                  + " found at " + "character: " + place.getCharacterOffset()
                  + ", line: " + place.getLineNumber() + ", column: "
                  + place.getColumnNumber());
        }
        // move to the next tag (or to the close tag for the batch)
        type = xsr.nextTag();
      }
      xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
              "batch");
      xsr.close();
    } finally {
      inputStream.close();
    }
    // now instantiate input and output handlers
    batch.setInputHandler(inputHandlerSpec.toInputHandler());
    List<OutputHandler> outputHandlers = new ArrayList<OutputHandler>(
            outputHandlerSpecs.size());
    for(HandlerSpec ohs : outputHandlerSpecs) {
      outputHandlers.add(ohs.toOutputHandler());
    }
    batch.setOutputHandlers(outputHandlers);

    // if no doc IDs or enumerators then assume streaming mode
    if(docIDsOrSpecs != null && docIDsOrSpecs.size() > 0) {
      List<DocumentID> docIds = new LinkedList<DocumentID>();
      for(Object item : docIDsOrSpecs) {
        if(item instanceof DocumentID) {
          docIds.add((DocumentID)item);
        } else if(item instanceof HandlerSpec) {
          DocumentEnumerator enumerator = ((HandlerSpec)item).toDocumentEnumerator();
          while(enumerator.hasNext()) docIds.add(enumerator.next()); 
        }
      }
  
      batch.setDocumentIDs(docIds.toArray(new DocumentID[docIds.size()]));
    }

    // check the batch got all the data it needed
    batch.init();
    return batch;
  }

  /**
   * Reads an input element.
   * 
   * @param xsr
   * @return
   * @throws GateException
   * @throws XMLStreamException
   */
  private static HandlerSpec extractInputHandler(XMLStreamReader xsr,
          File xmlFile) throws GateException, XMLStreamException {
    // <input class="boor.Bar" par1=var1 ... ./>
    xsr.require(XMLStreamConstants.START_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "input");
    HandlerSpec handlerSpec = new HandlerSpec();
    handlerSpec.configData = new HashMap<String, String>();
    String className = null;
    for(int i = 0; i < xsr.getAttributeCount(); i++) {
      String attrName = xsr.getAttributeLocalName(i);
      if(attrName.equalsIgnoreCase("class")) {
        className = xsr.getAttributeValue(i);
      } else {
        handlerSpec.configData.put(attrName, xsr.getAttributeValue(i));
      }
    }
    handlerSpec.configData.put(PARAM_BATCH_FILE_LOCATION, xmlFile.getAbsolutePath());
    if(className != null) {
      handlerSpec.className = className;
    } else {
      throw new GateException(
              "Attribute class (required) not provided for input config!");
    }
    xsr.nextTag();
    xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "input");
    return handlerSpec;
  }

  private static HandlerSpec extractOutputHandler(XMLStreamReader xsr,
          File xmlFile) throws XMLStreamException, GateException {
    // <output class="foo.Bar" ...
    xsr.require(XMLStreamConstants.START_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "output");
    // extract all the attributes
    HandlerSpec handlerSpec = new HandlerSpec();
    handlerSpec.configData = new HashMap<String, String>();
    for(int i = 0; i < xsr.getAttributeCount(); i++) {
      String attrName = xsr.getAttributeLocalName(i);
      if(attrName.equalsIgnoreCase("class")) {
        handlerSpec.className = xsr.getAttributeValue(i);
      } else {
        handlerSpec.configData.put(attrName, xsr.getAttributeValue(i));
      }
    }
    handlerSpec.configData.put(PARAM_BATCH_FILE_LOCATION, xmlFile.getAbsolutePath());
    // now we need to parse the contained nodes.
    handlerSpec.asDefs =
            new ArrayList<AnnotationSetDefinition>();
    int type = xsr.nextTag();
    while(type == XMLStreamReader.START_ELEMENT) {
      handlerSpec.asDefs.add(extractAnnotationSetDefinition(xsr));
      type = xsr.nextTag();
    }
    // sanity check
    xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "output");
    return handlerSpec;
  }

  
  /**
   * Reads an input element.
   * 
   * @param xsr
   * @return
   * @throws GateException
   * @throws XMLStreamException
   */
  private static HandlerSpec extractEnumerator(XMLStreamReader xsr,
          File xmlFile) throws GateException, XMLStreamException {
    // <documentEnumerator class="boor.Bar" par1=var1 ... ./>
    xsr.require(XMLStreamConstants.START_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "documentEnumerator");
    HandlerSpec handlerSpec = new HandlerSpec();
    handlerSpec.configData = new HashMap<String, String>();
    String className = null;
    for(int i = 0; i < xsr.getAttributeCount(); i++) {
      String attrName = xsr.getAttributeLocalName(i);
      if(attrName.equalsIgnoreCase("class")) {
        className = xsr.getAttributeValue(i);
      } else {
        handlerSpec.configData.put(attrName, xsr.getAttributeValue(i));
      }
    }
    handlerSpec.configData.put(PARAM_BATCH_FILE_LOCATION, xmlFile.getAbsolutePath());
    if(className != null) {
      handlerSpec.className = className;
    } else {
      throw new GateException(
              "Attribute class (required) not provided for input config!");
    }
    xsr.nextTag();
    xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "documentEnumerator");
    return handlerSpec;
  }
  
  private static AnnotationSetDefinition extractAnnotationSetDefinition(
          XMLStreamReader xsr) throws GateException, XMLStreamException {
    // <annotationSet name="...">
    xsr.require(XMLStreamConstants.START_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "annotationSet");
    String annSetName = xsr.getAttributeValue(XMLConstants.NULL_NS_URI, "name");
    // extract the annotation types, if any
    List<String> annTypes = new ArrayList<String>();
    int type = xsr.nextTag();
    while(type == XMLStreamConstants.START_ELEMENT) {
      xsr.require(XMLStreamConstants.START_ELEMENT,
              IOConstants.BATCH_NAMESPACE, "annotationType");
      String typeName = xsr.getAttributeValue(XMLConstants.NULL_NS_URI, "name");
      if(typeName != null) {
        annTypes.add(typeName);
      } else {
        Location place = xsr.getLocation();
        throw new GateException(
                "Element \"annotationType\" found with no name attribute at "
                        + "character: " + place.getCharacterOffset()
                        + ", line: " + place.getLineNumber() + ", column: "
                        + place.getColumnNumber());
      }
      // read the end element
      xsr.nextTag();
      xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
              "annotationType");
      // and start the next tag
      type = xsr.nextTag();
    }
    // sanity check
    xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "annotationSet");
    return new AnnotationSetDefinition(annSetName, annTypes);
  }

  /**
   * Parses the documents element in the input XML file and converts it into a 
   * list of values. Values are either:
   * <ul>
   *   <li>a {@link DocumentID}, representing a document ID</li>
   *   <li>a {@link HandlerSpec} (that will be converted to a {@link DocumentEnumerator}
   *   after the GATE application was loaded, and all its libraries were added 
   *   to the GATE classpath</li>
   * </ul>
   * @param xsr
   * @param xmlFile
   * @return
   * @throws GateException
   * @throws XMLStreamException
   */
  private static List<Object> extractDocumentIDs(XMLStreamReader xsr, File xmlFile)
          throws GateException, XMLStreamException {
    xsr.require(XMLStreamConstants.START_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "documents");
    List<Object> docIDsOrSpecs = new LinkedList<Object>();
    while(xsr.nextTag() == XMLStreamConstants.START_ELEMENT) {
      String elemName = xsr.getLocalName();
      if(elemName.equals("id")) {
        xsr.require(XMLStreamConstants.START_ELEMENT,
                IOConstants.BATCH_NAMESPACE, "id");
        Map<String, String> docIdAttrs = null;
        int attrCount = xsr.getAttributeCount(); 
        if(attrCount > 0) {
          docIdAttrs = new Object2ObjectArrayMap<String, String>(
              new String[attrCount], new String[attrCount]);
          for(int i = 0; i< attrCount; i++) {
            docIdAttrs.put(xsr.getAttributeName(i).toString(), 
              xsr.getAttributeValue(i));
          }
        }
        String documentIdText = xsr.getElementText();
        docIDsOrSpecs.add(new DocumentID(documentIdText, docIdAttrs));
        xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
                "id");
      } else if(elemName.equals("documentEnumerator")) {
        xsr.require(XMLStreamConstants.START_ELEMENT,
                IOConstants.BATCH_NAMESPACE, "documentEnumerator");
        HandlerSpec handlerSpec = extractEnumerator(xsr, xmlFile);
        docIDsOrSpecs.add(handlerSpec);
        xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
        "documentEnumerator");
      } else {
        // exception
        Location place = xsr.getLocation();
        throw new GateException(
                "Found element \"" + elemName + "\" starting at " + 
                "character: " + place.getCharacterOffset() + 
                ", line: " + place.getLineNumber() + ", column: " + 
                place.getColumnNumber() + 
                ". Was expecting one of \"<id>\" or \"<documentEnumerator>\".");
      }
    }
    xsr.require(XMLStreamConstants.END_ELEMENT, IOConstants.BATCH_NAMESPACE,
            "documents");
    return docIDsOrSpecs;
  }

  /**
   * "Struct" class holding the specification of an input or output
   * handler (or an enumerator) as parsed from the XML.
   */
  protected static class HandlerSpec {
    protected String className;
    protected Map<String, String> configData;
    protected List<AnnotationSetDefinition> asDefs;
    
    protected InputHandler toInputHandler() throws GateException {
      try {
        Class<? extends InputHandler> inputHandlerClass =
                Class.forName(className, true, Gate.getClassLoader())
                        .asSubclass(InputHandler.class);
        InputHandler inputHandler = inputHandlerClass.newInstance();
        inputHandler.config(configData);
        inputHandler.init();
        return inputHandler;
      } catch(Exception e) {
        throw new GateException(
                "Could not instantiate declared input handler.", e);
      }
    }
    
    protected OutputHandler toOutputHandler() throws GateException {
      try {
        Class<? extends OutputHandler> ouputHandlerClass =
          Class.forName(className, true, Gate.getClassLoader())
                 .asSubclass(OutputHandler.class);
        OutputHandler outHandler = ouputHandlerClass.newInstance();
        outHandler.config(configData);
        outHandler.setAnnSetDefinitions(asDefs);
        outHandler.init();
        return outHandler;
      } catch(Exception e) {
        throw new GateException(
                "Could not instantiate declared output handler.", e);
      }
    }
    
    protected DocumentEnumerator toDocumentEnumerator() throws GateException {
      try {
        Class<? extends DocumentEnumerator> enumeratorClass =
                Class.forName(className, true, Gate.getClassLoader())
                        .asSubclass(DocumentEnumerator.class);
        DocumentEnumerator enumerator = enumeratorClass.newInstance();
        enumerator.config(configData);
        enumerator.init();
        return enumerator;
      } catch(Exception e) {
        throw new GateException(
                "Could not instantiate declared document enumerator.", e);
      }
    }
  }

  public static void main(String... args) throws Exception {
    Gate.init();
    Batch aBatch = fromXml(new File("test/data/batch-test-0001.xml"));
    System.out.println(aBatch);
    if(Boolean.getBoolean("gate.cloud.util.XMLBatchParser.showDocIds")) {
      System.out.println("\nDocument IDs:");
      for(DocumentID aDocId : aBatch.getDocumentIDs()){
        System.out.println(aDocId.toString());
      }
    }
  }

}
