/*
 *  Tools.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: Tools.java 18208 2014-07-23 12:50:37Z ian_roberts $ 
 */
package gate.cloud.util;

import gate.cloud.batch.BatchJobData;
import gate.cloud.batch.ProcessResult;
import gate.cloud.batch.ProcessResult.ReturnCode;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Comment;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;

/**
 * This class contains some utility methods for working with XML report files.
 */
public class Tools {

  /**
   * Utility method to write a ProcessResult to an open XMLStreamWriter.
   * 
   * @param result the ProcessResult to write
   * @param writer the XMLStreamWriter to write to. This should be in
   *          the appropriate position to write a process result, i.e.
   *          writeStartDocument and writeStartElement(rootTag) should
   *          have been called, and the writer should be positioned
   *          either immediately after the root tag or after a previous
   *          process result.
   */
  public static void writeResultToXml(ProcessResult result,
          XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "processResult");
    writer.writeAttribute("id", result.getDocumentId().getIdText());
    writer.writeAttribute("returnCode", String.valueOf(result.getReturnCode()));
    //write the size of the file
    if(result.getOriginalFileSize() >= 0) {
      writer.writeStartElement(Tools.REPORT_NAMESPACE, "fileSize");
      writer.writeCharacters(String.valueOf(result.getOriginalFileSize()));
      writer.writeEndElement(); // fileSize
    }
    if(result.getDocumentLength() >= 0) {
      writer.writeStartElement(Tools.REPORT_NAMESPACE, "documentLength");
      writer.writeCharacters(String.valueOf(result.getDocumentLength()));
      writer.writeEndElement();
    }
    
    if(result.getExecutionTime() >= 0) {
      writer.writeStartElement(Tools.REPORT_NAMESPACE, "executionTime");
      writer.writeCharacters(String.valueOf(result.getExecutionTime()));
      writer.writeEndElement(); // executionTime
    }
    if(result.getReturnCode() == ReturnCode.SUCCESS) {
      writer.writeStartElement(Tools.REPORT_NAMESPACE, "statistics");
      if(result.getAnnotationCounts() != null) {
        for(Map.Entry<String, Integer> entry : result.getAnnotationCounts()
                .entrySet()) {
          writer.writeEmptyElement(Tools.REPORT_NAMESPACE, "annotationCount");
          writer.writeAttribute("type", entry.getKey());
          writer.writeAttribute("count", String.valueOf(entry.getValue()));
        }
      }
      writer.writeEndElement(); // statistics
    }
    else {
      // returnCode == FAIL - write an errorDescription if we have one
      if(result.getErrorDescription() != null) {
        writer.writeStartElement(Tools.REPORT_NAMESPACE, "errorDescription");
        writer.writeCharacters(result.getErrorDescription());
        writer.writeEndElement(); // errorDescription
      }
    }

    writer.writeEndElement(); // processResult
  }
  
  public static void writeBatchResultToXml(BatchJobData jobData, 
          XMLStreamWriter writer) throws XMLStreamException{
    writer.writeCharacters("\n");
    writer.writeComment(
        "This shows the overall execution time for the whole \n" + 
        "batch. This value only includes the execution time of \n" + 
        "the last run (so if the batch was restarted, it will \n" +
        "only include those documents that were processed last!");
    writer.writeCharacters("\n");
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "batchReport");
    writer.writeCharacters("\n  ");
    
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "finalBatchState");
    writer.writeCharacters(jobData.getState().toString());
    writer.writeEndElement(); writer.writeCharacters("\n  ");
    
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "totalDocuments");
    int totalDocs = jobData.getTotalDocumentCount();
    if(totalDocs < 0) {
      // streaming mode, so we don't know totaldocs up front, calculate it
      // from success and error
      totalDocs = jobData.getSuccessDocumentCount() + jobData.getErrorDocumentCount();
    }
    writer.writeCharacters(Integer.toString(totalDocs));
    writer.writeEndElement(); writer.writeCharacters("\n  ");
    
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "successfullyProcessed");
    writer.writeCharacters(Integer.toString(jobData.getSuccessDocumentCount()));
    writer.writeEndElement(); writer.writeCharacters("\n  ");
    
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "withError");
    writer.writeCharacters(Integer.toString(jobData.getErrorDocumentCount()));
    writer.writeEndElement(); writer.writeCharacters("\n  ");
    
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "totalBytes");
    writer.writeCharacters(Long.toString(jobData.getTotalFileSize()));
    writer.writeEndElement(); writer.writeCharacters("\n  ");
    
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "totalCharacters");
    writer.writeCharacters(Long.toString(jobData.getTotalDocumentLength()));
    writer.writeEndElement(); writer.writeCharacters("\n  ");    
    
    long endTime = System.currentTimeMillis();
    writer.writeStartElement(Tools.REPORT_NAMESPACE, "executionTime");
    writer.writeCharacters(Long.toString(endTime - jobData.getStartTime()));
    writer.writeEndElement(); writer.writeCharacters("\n  ");
    writer.writeEndElement(); writer.writeCharacters("\n");
  }
  

  /**
   * Write an XMLEvent to a stream writer.  This is not a complete
   * implementation, it only deals with the subset of events we are actually
   * interested in for report files (start element, along with its attributes
   * and namespace declarations, end element, character data and comments).
   *
   * @param e the XMLEvent to write
   * @param writer the writer to write to
   * @throws XMLStreamException
   */
  public static void writeStaxEvent(XMLEvent e, XMLStreamWriter writer)
            throws XMLStreamException {
    if(e.isStartElement()) {
      writer.writeStartElement(e.asStartElement().getName().getPrefix(),
                               e.asStartElement().getName().getLocalPart(),
                               e.asStartElement().getName().getNamespaceURI());
      // copy attributes
      @SuppressWarnings("unchecked")
      Iterator<Attribute> atts = e.asStartElement().getAttributes();
      while(atts.hasNext()) {
        Attribute a = atts.next();
        writer.writeAttribute(a.getName().getPrefix(),
                              a.getName().getNamespaceURI(),
                              a.getName().getLocalPart(),
                              a.getValue());
      }
      // copy namespaces
      @SuppressWarnings("unchecked")
      Iterator<Namespace> ns = e.asStartElement().getNamespaces();
      while(ns.hasNext()) {
        Namespace n = ns.next();
        writer.writeNamespace(n.getPrefix(), n.getNamespaceURI());
      }
    }
    else if(e.isEndElement()) {
      writer.writeEndElement();
    }
    else if(e.isCharacters()) {
      if(e.asCharacters().isCData()) {
        writer.writeCData(e.asCharacters().getData());
      }
      else {
        writer.writeCharacters(e.asCharacters().getData());
      }
    }
    else if(e instanceof Comment) {
      writer.writeComment(((Comment)e).getText());
    }

    // other event types are ignored
  }


  /**
   * XML namespace used for all elements in a batch definition XML file.
   */
  public static final String REPORT_NAMESPACE =
          "http://gate.ac.uk/ns/cloud/report/1.0";
  
}
