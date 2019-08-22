package gate.cloud.io.arc;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.commons.httpclient.Header;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.HeaderedArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.io.warc.WARCRecord;

public class WARCInputHandler extends ArchiveInputHandler {

  @Override
  protected ArchiveReader createReader() throws IOException {
    return WARCReaderFactory.get(srcFile);
  }

  @Override
  protected ArchiveRecord getRecord(ArchiveReader reader, long offset)
          throws IOException {
    return new HeaderedArchiveRecord(reader.get(offset), true);
  }

  @Override
  protected ArchiveRecord archiveRecordFromByteArray(byte[] data, String url)
          throws IOException {
    return new HeaderedArchiveRecord(
            new WARCRecord(new ByteArrayInputStream(data), url, 0, false, false),
            true);
  }

  @Override
  protected Header[] httpHeaders(ArchiveRecord record) {
    return ((HeaderedArchiveRecord)record).getContentHeaders();
  }

}
