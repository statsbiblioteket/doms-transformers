package dk.statsbiblioteket.doms.transformers.objectcreator;

import dk.statsbiblioteket.doms.transformers.common.FileNameParser;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.BroadcastFileDescriptiveMetadataType;
import dk.statsbiblioteket.doms.transformers.common.muxchannels.MuxFileChannelCalculator;

import java.text.ParseException;

public class DomsObject {
    private static String baseUrl;

    private String fileName;
    private String checksum;
    private String size;
    private BroadcastFileDescriptiveMetadataType metadata;

    public DomsObject(String fileName,
                      String checksum,
                      String size,
                      MuxFileChannelCalculator muxFileChannelCalculator)
            throws ParseException {

        if (getBaseUrl() == null) {
            setBaseUrl("");
        }

        this.fileName = fileName;
        this.checksum = checksum;
        this.size = size;
        metadata = FileNameParser.decodeFilename(this.fileName, checksum, muxFileChannelCalculator);
    }

    public String toString() {
        return String.format(
                "%s <file:%s, size:%s, checksum:%s, format:%s, url:%s>",
                getClass().getName(),
                getFileName(),
                getSize(),
                getChecksum(),
                getFormat(),
                getPermanentUrl()
        );
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static void setBaseUrl(String newBaseUrl) {
        baseUrl = newBaseUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getPermanentUrl() {
        return baseUrl + fileName;
    }

    public String getFormat() {
        return metadata.getFormat();
    }

    public String getSize() {
        return size;
    }
}
