package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_FILE_EXTENSION;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Utils;
import gate.cloud.batch.DocumentID;
import gate.corpora.DocumentJsonUtils;
import gate.util.GateException;
import gate.util.OffsetComparator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;

/**
 * Output handler to output documents in (Twitter-style) JSON format as
 * produced by GATE's {@link DocumentJsonUtils}. Adds some custom
 * parameters:
 * 
 * <dl>
 * <dt>groupEntitiesBy</dt>
 * <dd>How to group annotations into "entities". The JSON format
 * represents annotations as a map from some label to an array of
 * annotation instances - individual annotations do not have a type as
 * such. This parameter can take the value "set", which produces one
 * group per <i>annotation set name</i> (with the default, un-named set
 * using the label "default"), or "type", which produces one group per
 * <i>annotation type</i> (e.g. "Person", "Location", with annotations
 * of the same type from different sets grouped together).</dd>
 * 
 * <dt>documentAnnotationASName</dt>
 * <dt>documentAnnotationType</dt>
 * <dd>If these parameters are set, the specified annotation set is
 * expected to contain <i>one</i> annotation of the specified type which
 * covers the "interesting" content. Only this segment of the document
 * will be output, and the features of the document annotation will be
 * merged in as top-level properties of the resulting JSON. This is
 * typically used for documents that originated in JSON format, for
 * example those parsed by the Twitter JSON document format in GATE's
 * Twitter plugin (for which "Original markups" would be the AS name and
 * "Tweet" the annotation type).</dd>
 * 
 * <dt>annotationTypeProperty</dt>
 * <dd>If specified, the type of each annotation is included as a
 * "feature" in the JSON output. Typically this option would be set if
 * groupEntitiesBy is "set", and not if groupEntitiesBy is "type".</dd>
 * </dl>
 * 
 * @author ian
 * 
 */
public class JSONOutputHandler extends AbstractFileOutputHandler {

  private static final JsonFactory JSON_FACTORY = new JsonFactory()
          .enable(Feature.AUTO_CLOSE_TARGET);

  public static final String PARAM_GROUP_ENTITIES_BY = "groupEntitiesBy";

  public static final String PARAM_DOCUMENT_ANNOTATION_TYPE =
          "documentAnnotationType";

  public static final String PARAM_DOCUMENT_ANNOTATION_AS_NAME =
          "documentAnnotationASName";

  public static final String PARAM_ANNOTATION_TYPE_PROPERTY =
          "annotationTypeProperty";

  protected String groupEntitiesBy;

  protected String documentAnnotationType;

  protected String documentAnnotationASName;

  protected String annotationTypeProperty;

  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // make sure we default to .json as the extension
    if(!configData.containsKey(PARAM_FILE_EXTENSION)) {
      configData.put(PARAM_FILE_EXTENSION, ".json");
    }
    groupEntitiesBy = configData.get(PARAM_GROUP_ENTITIES_BY);
    if(groupEntitiesBy == null || groupEntitiesBy.length() == 0) {
      groupEntitiesBy = "type";
    }
    documentAnnotationType = configData.get(PARAM_DOCUMENT_ANNOTATION_TYPE);
    documentAnnotationASName =
            configData.get(PARAM_DOCUMENT_ANNOTATION_AS_NAME);
    annotationTypeProperty = configData.get(PARAM_ANNOTATION_TYPE_PROPERTY);
    super.configImpl(configData);
  }

  @Override
  protected void outputDocumentImpl(Document document, DocumentID documentId)
          throws IOException, GateException {
    Map<String, Collection<Annotation>> annotationSetsMap =
            collectAnnotations(document);
    // if groupEntitiesBy == "type" then we need to "invert" the map
    if("type".equals(groupEntitiesBy)) {
      Map<String, Collection<Annotation>> originalMap = annotationSetsMap;
      annotationSetsMap = new HashMap<String, Collection<Annotation>>();
      for(Collection<Annotation> annSet : originalMap.values()) {
        for(Annotation a : annSet) {
          Collection<Annotation> annsByType =
                  annotationSetsMap.get(a.getType());
          if(annsByType == null) {
            annsByType = new ArrayList<Annotation>();
            annotationSetsMap.put(a.getType(), annsByType);
          }
          annsByType.add(a);
        }
      }
      Comparator<Annotation> comparator = new OffsetComparator();
      for(Collection<Annotation> annList : annotationSetsMap.values()) {
        Collections.sort((List<Annotation>)annList, comparator);
      }
    } else {
      // otherwise, if groupEntitiesBy == "set" then we only need to
      // replace the null mapping for the default set with a "default"
      // one
      if(annotationSetsMap.containsKey(null)) {
        annotationSetsMap.put("default", annotationSetsMap.remove(null));
      }
    }

    // open the output file
    OutputStream outputStream = getFileOutputStream(documentId);
    OutputStreamWriter writer =
            new OutputStreamWriter(outputStream, (encoding == null
                    || encoding.length() == 0 ? "UTF-8" : encoding));
    JsonGenerator generator = JSON_FACTORY.createGenerator(writer);
    try {
      if(documentAnnotationType != null && documentAnnotationType.length() > 0) {
        AnnotationSet documentAnnotationSet =
                document.getAnnotations(documentAnnotationASName).get(
                        documentAnnotationType);
        if(documentAnnotationSet.size() > 1) {
          throw new GateException("Found more than one "
                  + documentAnnotationType + " annotation for document "
                  + documentId);
        }
        if(documentAnnotationSet.size() > 0) {
          Annotation documentAnnotation =
                  Utils.getOnlyAnn(documentAnnotationSet);
          DocumentJsonUtils.writeDocument(document,
                  Utils.start(documentAnnotation),
                  Utils.end(documentAnnotation), annotationSetsMap,
                  documentAnnotation.getFeatures(), annotationTypeProperty,
                  generator);
          return;
        }
      }

      // if we get here we either didn't have documentAnnotationType
      // set, or it was set but the document contained no such
      // annotation - simply output the whole document with no extra
      // features.
      DocumentJsonUtils.writeDocument(document, 0L, Utils.end(document),
              annotationSetsMap, null, annotationTypeProperty, generator);
    } finally {
      generator.close();
    }
  }
}
