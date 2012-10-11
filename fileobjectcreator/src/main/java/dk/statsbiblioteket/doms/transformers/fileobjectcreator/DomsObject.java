package dk.statsbiblioteket.doms.transformers.fileobjectcreator;

import dk.statsbiblioteket.doms.transformers.common.FileNameParser;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.BroadcastMetadata;
import dk.statsbiblioteket.doms.transformers.common.muxchannels.MuxFileChannelCalculator;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class DomsObject {
    private static Map<String, String> nameToUriMap = null;
    private static String baseUrl;

    private String fileName;
    private String checksum;
    private String size;
    private BroadcastMetadata metadata;

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

    public String formatAsInput() {
        return String.format(
                "%s %s %s",
                getChecksum(),
                getSize(),
                getFileName());
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

    public Map<String, String> toChecksumMap() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(getFileName(), getChecksum());

        return map;
    }

    public String guessFormatUri() {
        if (nameToUriMap == null) {
            nameToUriMap = new HashMap<String, String>();
            nameToUriMap.put("mpegts-multichannel-video", null); // shouldn't be used directly, needs furhter processing.
            nameToUriMap.put("mpeg1", "info:pronom/x-fmt/385");
            nameToUriMap.put("mpeg2", "info:pronom/x-fmt/386");
            nameToUriMap.put("asf", "info:pronom/fmt/133");
            nameToUriMap.put("mp4", "info:pronom/fmt/199");
            nameToUriMap.put("wav", "info:pronom/fmt/6");
        }

        String formatName = metadata.getFormat();
        String formatUri = null;

        if (nameToUriMap.containsKey(formatName)) {
            formatUri = nameToUriMap.get(formatName);

            if (formatUri == null) { // this is a transport stream, and must be handled specially.
                if (fileName.startsWith("mux1")) {
                    formatUri = "info:mime/video/MP2T;codecs=\"dvbsub,mp1,mp2,mpeg2video\"";
                } else if (fileName.startsWith("mux2")) {
                    formatUri = "info:mime/video/MP2T;codecs=\"dvbsub,mp2,mpeg2video\"";
                }
            }
        }
        return formatUri;
    }
}
