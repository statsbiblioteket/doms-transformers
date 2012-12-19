package dk.statsbiblioteket.doms.transformers.fileobjectcreator;

import dk.statsbiblioteket.doms.transformers.common.CalendarUtils;
import dk.statsbiblioteket.doms.transformers.common.ChannelIDToSBChannelIDMapper;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.BroadcastMetadata;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.Channel;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.Channels;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class FileNameParser {
    private final String filename;
    private final String checksum;
    private String size;
    private final MuxFileChannelCalculator muxChannelCalculator;

    public FileNameParser(String filename, String checksum, String size, MuxFileChannelCalculator muxChannelCalculator) {
        this.filename = filename;
        this.checksum = checksum;
        this.size = size;
        this.muxChannelCalculator = muxChannelCalculator;
    }

    public BroadcastMetadata getBroadCastMetadata() throws ParseException {
        return decodeFilename(filename, checksum, size, muxChannelCalculator);
    }

    private BroadcastMetadata decodeFilename(String filename,
                                                   Map<String, String> checksums,
                                                   String size,
                                                   MuxFileChannelCalculator muxChannelCalculator)
            throws ParseException {

        if (checksums.containsKey(filename)) {
            return decodeFilename(filename, checksums.get(filename), size, muxChannelCalculator);
        } else {
            return null;
        }
    }

    private BroadcastMetadata decodeFilename(String filename,
                                                   String checksum,
                                                   String size,
                                                   MuxFileChannelCalculator muxChannelCalculator)
            throws ParseException {

        BroadcastMetadata result;

        if (filename.startsWith("mux") && filename.endsWith(".ts")) {
            result = decodeMuxFilename(filename, muxChannelCalculator);
        } else if (filename.endsWith(".wav")) {
            result = decodeRadioFilename(filename);
        } else if (filename.endsWith(".mpeg") || filename.endsWith(".wmv") || filename.endsWith(".mp4")) {
            result = decodeAnalogTVFilename(filename);
        } else if (filename.endsWith("_yousee.ts")) {
            result = decodeYouseeFilename(filename, size);
        } else {
            throw new ParseException(String.format("Failed to match filename '%s' to any of the handled cases", filename), 0);
        }

        if (result != null) {
            result.setChecksum(checksum);
            if (result.getRecorder() == null) {
                result.setRecorder(""); // Horrible hack: very old files, like "dk4_807.250_K63-DK4_mpeg1_20050919075001_20050920074502.mpeg", has no recorder information, yet it is required in the xsd.
            }
        }

        return result;
    }

    private BroadcastMetadata decodeMuxFilename(String filename, MuxFileChannelCalculator muxChannelCalculator) {
        //mux1.1287514800-2010-10-19-21.00.00_1287518400-2010-10-19-22.00.00_dvb1-1.ts
        //(type).(timestart)-(timestart)_(timeend)-(timeEnd)_(recorder).ts
        BroadcastMetadata metadata = new BroadcastMetadata();

        String startUnixTime = filename.split("\\.")[1].split("-")[0];
        String stopUnixTime = filename.split("_")[1].split("-")[0];
        String recorder = filename.split("_")[2].split("\\.")[0];
        String muxName = filename.split("\\.")[0];
        int muxID = Integer.parseInt(muxName.split("mux")[1]);

        Channels channels = new Channels();
        channels.getChannel().addAll(muxChannelCalculator.getChannelIDsForMux(muxID, CalendarUtils.getDate(startUnixTime)));

        for (int i = 0; i < channels.getChannel().size(); i++) {
            Channel channelID = channels.getChannel().get(i);
            channelID = ChannelIDToSBChannelIDMapper.getInstance().mapToSBChannel(channelID);
            channels.getChannel().set(i, channelID);
        }

        metadata.setFormat("mpegts-multichannel-video");

        metadata.setFilename(filename);
        metadata.setChannels(channels);
        metadata.setStartTime(CalendarUtils.getXmlGregorianCalendar(startUnixTime));
        metadata.setStopTime(CalendarUtils.getXmlGregorianCalendar(stopUnixTime));
        metadata.setRecorder(recorder);

        return metadata;
    }

    private BroadcastMetadata decodeYouseeFilename(String filename, String size) {
        //tv2c_yousee.1355684400-2012-12-16-20.00.00_1355688000-2012-12-16-21.00.00_yousee.ts
        //(channelID)_yousee.(timestart)-(timestart)_(timeend)-(timeEnd)_(recorder).ts

        long numericSize = Long.parseLong(size.trim());

        BroadcastMetadata metadata = new BroadcastMetadata();

        String startUnixTime = filename.split("\\.")[1].split("-")[0];
        String stopUnixTime = filename.split("_")[2].split("-")[0];
        String recorder = filename.split("_")[2].split("\\.")[0];
        String channelId = filename.split("\\.")[0];

        Channels channels = new Channels();
        Channel channel = new Channel();
        // map channelId to ID and set it here.. etc.. or reuse method if applicable from decodeMuxFilename
        channel.setChannelID(channelId);
        channels.getChannel().add(channel);


        // this should never be used in production, and is only meant for testing purposes.
        // if a file is larger than 256MB it is assumed to be a video-file, otherwise a radio file.
        // if we aren't dealing with 1 hour files, this will most likely fail horrible.
        if (numericSize > 256*1024*1024) {
            metadata.setFormat("mpegts-singlechannel-video");
        } else {
            metadata.setFormat("mpegts-singlechannel-audio");
        }

        metadata.setFilename(filename);
        metadata.setChannels(channels);
        metadata.setStartTime(CalendarUtils.getXmlGregorianCalendar(startUnixTime));
        metadata.setStopTime(CalendarUtils.getXmlGregorianCalendar(stopUnixTime));
        metadata.setRecorder(recorder);

        return metadata;
    }

    private BroadcastMetadata decodeRadioFilename(String filename) throws ParseException {
        //drp1_88.100_DR-P1_pcm_20080509045602_20080510045501_encoder5-2.wav
        //(channelID)_(frequency)_(CHANNELID)_(format)_(timeStart)_(timeEnd)_(recorder).wav
        BroadcastMetadata metadata = new BroadcastMetadata();
        Channels channels = new Channels();

        String[] tokens = filename.split("_");
        String channelID = tokens[0];
        String formatName = "wav";
        String timeStart = tokens[4];
        String timeStop = tokens[5];
        String recorder = null;
        if (tokens.length >= 7) {
            recorder = tokens[6].split("\\.")[0];
        }

        DateFormat dateformat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date timeStartDate = dateformat.parse(timeStart);
        Date timeStopDate = dateformat.parse(timeStop);


        String mappedChannelId = ChannelIDToSBChannelIDMapper.getInstance().mapToSBChannelID(channelID);
        Channel channel = new Channel();
        channel.setChannelID(mappedChannelId);

        channels.getChannel().add(channel);
        metadata.setFilename(filename);
        metadata.setChannels(channels);
        metadata.setFormat(formatName);
        metadata.setRecorder(recorder);
        metadata.setStartTime(CalendarUtils.getXmlGregorianCalendar(timeStartDate));
        metadata.setStopTime(CalendarUtils.getXmlGregorianCalendar(timeStopDate));

        return metadata;

    }

    private BroadcastMetadata decodeAnalogTVFilename(String filename) throws ParseException {
        //tv2c_623.250_K40-TV2-Charlie_mpeg1_20080503121001_20080504030601_encoder3-2.mpeg
        //kanal4_359.250_K42-Kanal4_mpeg1_20101023195601_20101023231601_encoder7-2.mpeg
        //tv3_161.250_S09-TV3_mpeg1_20101021175601_20101022010602_encoder6-2.mpeg
        //(channelID)_(frequency)_(CHANNELID)_(format)_(timeStart)_(timeEnd)_(recorder).mpeg
        BroadcastMetadata metadata = new BroadcastMetadata();
        Channels channels = new Channels();

        String[] tokens = filename.split("_");

        String channelID = tokens[0];
        String format = tokens[3];
        String timeStart = tokens[4];
        String timeStop = tokens[5];
        String recorder = null;
        if (tokens.length >= 7) {
            recorder = tokens[6].split("\\.")[0];
        }
        String formatName;
        if (format.equals("mpeg1")) {
            formatName = "mpeg1";
        } else if (format.equals("mpeg2")) {
            formatName = "mpeg2";
        } else if (format.equals("wmv")) {
            formatName = "asf";
        } else if (format.equals("mp4")) {
            formatName = "mp4";
        } else {
            throw new ParseException("Failed to parse format string '" + format + "'", 0);
        }

        DateFormat dateformat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date timeStartDate = dateformat.parse(timeStart);
        Date timeStopDate = dateformat.parse(timeStop);

        channels.getChannel().add(ChannelIDToSBChannelIDMapper.getInstance().mapToSBChannel(channelID));
        metadata.setFilename(filename);
        metadata.setChannels(channels);
        metadata.setFormat(formatName);
        metadata.setRecorder(recorder);
        metadata.setStartTime(CalendarUtils.getXmlGregorianCalendar(timeStartDate));
        metadata.setStopTime(CalendarUtils.getXmlGregorianCalendar(timeStopDate));

        return metadata;
    }
}
