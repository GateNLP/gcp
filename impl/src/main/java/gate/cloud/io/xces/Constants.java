/*
 *  Constants.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: Constants.java 13582 2011-03-29 16:23:58Z ian_roberts $ 
 */
package gate.cloud.io.xces;

/**
 * XCES-related constants.
 */
public class Constants {
  
  public static final String XCES_VERSION = "1.0";
  public static final String XCES_NAMESPACE = "http://www.xces.org/schema/2003";
  public static final String ANNO_TAG = "struct";
  public static final String ANNO_TYPE = "type";
  public static final String ANNO_FROM = "from";
  public static final String ANNO_TO = "to";
  public static final String ATT_TAG = "feat";
  public static final String ATT_NAME = "name";
  public static final String ATT_VALUE = "value";

  /**
   * Private constructor - this class should not be instantiated.
   */
  private Constants() {
  }
}
