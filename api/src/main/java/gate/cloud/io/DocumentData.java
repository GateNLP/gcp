/*
 *  DocumentData.java
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: DocumentData.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;

import gate.Document;
import gate.cloud.batch.DocumentID;

/**
 * Holder for a GATE document plus some metadata extracted at loading
 * time.
 */
public class DocumentData {
  public DocumentData(Document document, DocumentID id) {
    this.document = document;
    if(document != null) {
      this.documentLength = document.getContent().size();
    }
    this.id = id;
  }
  
  public DocumentID id;
  public Document document;
  public long fileSize = -1;
  public long documentLength = -1;
  public long processingTime = -1;
}
