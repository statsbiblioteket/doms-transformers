package dk.statsbiblioteket.doms.transformers.fileenricher;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import dk.statsbiblioteket.doms.central.InvalidCredentialsException;
import dk.statsbiblioteket.doms.central.InvalidResourceException;
import dk.statsbiblioteket.doms.central.MethodFailedException;
import dk.statsbiblioteket.doms.transformers.common.ChannelIDToSBChannelIDMapper;
import dk.statsbiblioteket.doms.transformers.common.DomsConfig;
import dk.statsbiblioteket.doms.transformers.common.FileNameParser;
import dk.statsbiblioteket.doms.transformers.common.ObjectHandler;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.BroadcastFileDescriptiveMetadataType;
import dk.statsbiblioteket.doms.transformers.common.autogenerated.ObjectFactory;
import dk.statsbiblioteket.doms.transformers.common.checksums.ChecksumParser;
import dk.statsbiblioteket.doms.transformers.common.muxchannels.MuxFileChannelCalculator;

/**
 * Use DOMS to enrich file metadata.
 */
public class DomsFileEnricherObjectHandler implements ObjectHandler {

    private final DomsConfig config;
    private final CentralWebservice webservice;
    private ChannelIDToSBChannelIDMapper channelIDMapper = ChannelIDToSBChannelIDMapper.getInstance();
    private MuxFileChannelCalculator muxChannelCalculator;
    private Marshaller marshaller;
    private ObjectHandler delegate;
    private Map<String, String> checksums;

    /**
     * Initialise object handler.
     *
     * @param config     Configuration.
     * @param webservice The DOMS WebService.
     * @param checksums
     * @throws URISyntaxException 
     * @throws ParseException 
     * @throws IOException 
     */
    public DomsFileEnricherObjectHandler(FileEnricherConfig config, CentralWebservice webservice, ChecksumParser checksums, ObjectHandler delegate) throws JAXBException, IOException, ParseException, URISyntaxException {
        this.config = config;
        this.webservice = webservice;
        this.delegate = delegate;
        this.checksums = checksums.getNameChecksumsMap();
        this.muxChannelCalculator = new MuxFileChannelCalculator(
                Thread.currentThread().getContextClassLoader().getResourceAsStream("muxChannels.csv"));
        
        marshaller = JAXBContext.newInstance("dk.statsbiblioteket.doms.transformers.common.autogenerated").createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

    }

    @Override
    public void transform(String uuid) throws Exception {
        webservice.markInProgressObject(Arrays.asList(uuid), "Modifying object as part of datamodel upgrade");
        if (delegate != null) {
            delegate.transform(uuid);
        }
        String filename = getFilenameFromObject(uuid);
        BroadcastFileDescriptiveMetadataType metadata
                = FileNameParser.decodeFilename(filename, checksums, muxChannelCalculator);
        storeMetadataInObject(uuid, metadata);
        webservice.markPublishedObject(Arrays.asList(uuid), "Modifying object as part of datamodel upgrade");
    }


    public void storeMetadataInObject(String uuid, BroadcastFileDescriptiveMetadataType metadata) throws InvalidCredentialsException, MethodFailedException, InvalidResourceException, JAXBException {
        StringWriter writer = new StringWriter();
        JAXBElement<BroadcastFileDescriptiveMetadataType> blob = new ObjectFactory().createBroadcastFileDescriptiveMetadata(metadata);
        marshaller.marshal(blob, writer);
        String contents = writer.toString();
        webservice.modifyDatastream(uuid, "BROADCAST_METADATA", contents, "Updating metadata as part of the radio/tv datamodel refactoring");
    }

    public String getFilenameFromObject(String uuid) throws InvalidCredentialsException, MethodFailedException, InvalidResourceException {
        String label = webservice.getObjectProfile(uuid).getTitle();
        String filename = label.substring(label.lastIndexOf("/") + 1);
        return filename;
    }
}
