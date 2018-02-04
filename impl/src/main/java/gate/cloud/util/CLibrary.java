/*
 *  CLibrary.java
 *  Copyright (c) 2007-2013, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: CLibrary.java 17051 2013-11-06 13:28:22Z ian_roberts $ 
 */
package gate.cloud.util;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * JNA library definition to provide access to <code>getpid</code> from
 * libc.
 */
public interface CLibrary extends Library {
  public static final CLibrary INSTANCE = (CLibrary)Native.loadLibrary("c",
          CLibrary.class);

  int getpid();
}
