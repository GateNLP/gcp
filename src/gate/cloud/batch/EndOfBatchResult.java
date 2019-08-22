package gate.cloud.batch;

import java.util.Map;

public class EndOfBatchResult implements ProcessResult {

  public ReturnCode getReturnCode() {
    return ReturnCode.END_OF_BATCH;
  }

  public long getExecutionTime() {
    return 0;
  }

  public long getOriginalFileSize() {
    return 0;
  }

  public long getDocumentLength() {
    return 0;
  }

  public Map<String, Integer> getAnnotationCounts() {
    return null;
  }

  public DocumentID getDocumentId() {
    return null;
  }

  public String getErrorDescription() {
    return null;
  }
}
