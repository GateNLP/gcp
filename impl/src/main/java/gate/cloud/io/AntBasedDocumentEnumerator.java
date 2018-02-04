/*
 *  AntBasedDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: AntBasedDocumentEnumerator.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;

import gate.cloud.batch.DocumentID;
import gate.util.GateException;
import static gate.cloud.io.IOConstants.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Resource;

/**
 * Document enumerator that uses and Ant FileSet to do its work.
 */
public abstract class AntBasedDocumentEnumerator implements DocumentEnumerator {

  /**
   * File name patterns to include (by default, include everything).
   */
  protected String includes;

  /**
   * File name patterns to exclude (by default, exclude nothing).
   */
  protected String excludes;

  /**
   * Optional prefix to add to the document IDs returned.
   */
  protected String prefix;

  /**
   * Should the FileSet use Ant's default exclude patterns (for things
   * like .svn directories)? Defaults to true.
   */
  protected boolean defaultExcludes;

  /**
   * The underlying iterator from the FileSet.
   */
  protected Iterator<String> antIterator;

  public void config(Map<String, String> configData) throws IOException,
          GateException {
    includes = configData.get(PARAM_INCLUDES);
    excludes = configData.get(PARAM_EXCLUDES);
    prefix = configData.get(PARAM_FILE_NAME_PREFIX);
    if(prefix == null) {
      prefix = "";
    }
    String defaultExcludesStr = configData.get(PARAM_DEFAULT_EXCLUDES);
    if(defaultExcludesStr == null) {
      defaultExcludes = true;
    }
    else {
      defaultExcludes = Project.toBoolean(defaultExcludesStr);
    }
  }

  public void init() throws IOException, GateException {
    FileSet fs = createFileSet();
    if(includes != null) {
      fs.setIncludes(includes);
    }
    if(excludes != null) {
      fs.setExcludes(excludes);
    }
    fs.setDefaultexcludes(defaultExcludes);
    String[] matchedPaths =
            fs.getDirectoryScanner(new Project()).getIncludedFiles();
    // sort the results, to ensure predictability
    Arrays.sort(matchedPaths);
    antIterator = Arrays.asList(matchedPaths).iterator();
  }

  /**
   * Creates and returns the basic FileSet object that we will be
   * iterating over. Subclasses need to create an appropriate FileSet
   * configured for the right base directory or archive file, this class
   * will then handle the include and exclude patterns.
   */
  protected abstract FileSet createFileSet();

  public boolean hasNext() {
    return antIterator.hasNext();
  }

  public DocumentID next() {
    return new DocumentID(prefix + antIterator.next());
  }

  public void remove() {
    throw new UnsupportedOperationException("remove not supported");
  }

}
