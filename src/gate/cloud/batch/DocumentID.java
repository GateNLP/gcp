/*
 *  DocumentID.java
 *  Copyright (c) 2007-2013, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: DocumentID.java 17074 2013-11-11 13:48:34Z valyt $ 
 */
package gate.cloud.batch;

import gate.cloud.io.InputHandler;
import gate.cloud.io.file.FileInputHandler;

import java.util.Map;

/**
 * Class representing a document ID. When listed inside a batch XML
 * representation, document IDs are expressed like:
 * <pre>
 * &lt;id attr1=val1, attr2=val2, ...>idText&lt;/id>
 * </pre>
 * All of the components (the id text and the element attributes) are
 * optional. At limit, <tt>&lt;id/></tt> is a valid document ID; the only
 * constraint being that whatever {@link InputHandler} implementation is used
 * can deal with it.
 * 
 * In practice, most document IDs consist of only the ID text. For example, 
 * when a {@link FileInputHandler} is used, the id text contains the path of 
 * the file used to generate the document, relative to the document root 
 * directory.
 * 
 * Values of this class are immutable.
 */
public class DocumentID {
  
  /**
   * The text of this ID
   */
  private String idText;
  
  /**
   * The array of attributes, if any were provided. Each element in this 
   * array has size 2, and comprises an attribute name and an attribute value.
   */
  private Map<String, String> attributes;

  /**
   * Cached copy of the toString representation.
   */
  private String toString;
  
  public DocumentID(String idText) {
    this(idText, null);
  }
  
  public DocumentID(String idText, Map<String, String> attributes) {
    this.idText = idText;
    this.attributes = attributes;
  }
  
  /**
   * @return the idText
   */
  public String getIdText() {
    return idText;
  }

  /**
   * @return the attributes
   */
  public Map<String, String> getAttributes() {
    return attributes;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    if(toString == null) {
      if(attributes != null && attributes.size() > 0) {
        // xml-like representation
        StringBuilder str = new StringBuilder("<id");
        for(Map.Entry<String, String> attr : attributes.entrySet()) {
          str.append(' ').append(attr.getKey()).append("=\"")
            .append(attr.getValue()).append("\"");
        }
        if(idText != null){
          str.append(">").append(idText).append("</id>");
        } else {
          str.append("/>");
        }
        toString = str.toString();
      } else {
        // plain text ID
        toString = idText != null ? idText : "";
      }
    }
    return toString;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result =
        prime * result + ((attributes == null) ? 0 : attributes.hashCode());
    result = prime * result + ((idText == null) ? 0 : idText.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if(this == obj) return true;
    if(obj == null) return false;
    if(getClass() != obj.getClass()) return false;
    DocumentID other = (DocumentID)obj;
    if(attributes == null) {
      if(other.attributes != null) return false;
    } else if(!attributes.equals(other.attributes)) return false;
    if(idText == null) {
      if(other.idText != null) return false;
    } else if(!idText.equals(other.idText)) return false;
    return true;
  }
}