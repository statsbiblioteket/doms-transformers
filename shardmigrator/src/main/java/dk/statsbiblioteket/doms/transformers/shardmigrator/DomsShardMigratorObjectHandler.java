package dk.statsbiblioteket.doms.transformers.shardmigrator;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import dk.statsbiblioteket.doms.central.InvalidCredentialsException;
import dk.statsbiblioteket.doms.central.InvalidResourceException;
import dk.statsbiblioteket.doms.central.MethodFailedException;
import dk.statsbiblioteket.doms.central.Relation;
import dk.statsbiblioteket.doms.transformers.common.CalendarUtils;
import dk.statsbiblioteket.doms.transformers.common.ObjectHandler;
import dk.statsbiblioteket.doms.transformers.common.PropertyBasedDomsConfig;
import dk.statsbiblioteket.doms.transformers.shardmigrator.programBroadcast.autogenerated.ProgramBroadcast;
import dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.MissingEnd;
import dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.MissingStart;
import dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.ProgramStructure;
import dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.ProgramStructure.Holes;
import dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.ProgramStructure.Overlaps;
import dk.statsbiblioteket.doms.transformers.shardmigrator.shardmetadata.autogenerated.Hole;
import dk.statsbiblioteket.doms.transformers.shardmigrator.shardmetadata.autogenerated.Overlap;
import dk.statsbiblioteket.doms.transformers.shardmigrator.shardmetadata.autogenerated.ShardMetadata;
import dk.statsbiblioteket.doms.transformers.shardmigrator.shardmetadata.autogenerated.ShardStructure;
import dk.statsbiblioteket.doms.transformers.shardmigrator.tvmeter.autogenerated.TvmeterProgram;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.XPathSelector;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Use DOMS to handle object transformation for shard removal for a UUID.
 */
public class DomsShardMigratorObjectHandler implements ObjectHandler {

    private final PropertyBasedDomsConfig config;
    private CentralWebservice webservice;
    private TVMeterReader tvmeterReader;


    /**
     * Initialise object handler.
     * @param config Configuration.
     */
    public DomsShardMigratorObjectHandler(PropertyBasedDomsConfig config, CentralWebservice webservice) throws IOException {
        this.webservice = webservice;
        this.config = config;
        tvmeterReader = new TVMeterReader();
    }

    @Override
    public void transform(String programUuid) throws Exception {
        List<Relation> shardRelations = webservice.getNamedRelations(programUuid, "http://doms.statsbiblioteket.dk/relations/default/0/1/#hasShard");
        if (shardRelations.isEmpty()) {
            // Nothing to do
            return;
        }

        String shardUuid = shardRelations.get(0).getObject();
        List<Relation> consistsOfRelations = webservice.getNamedRelations(shardUuid,
                "http://doms.statsbiblioteket.dk/relations/default/0/1/#consistsOf");

        //get pbcore
        String pbcoreOriginal = webservice.getDatastreamContents(programUuid, "PBCORE");
        //initialise the migrator
        //PBCoreMigrator pbCoreMigrator = new PBCoreMigrator(new ByteArrayInputStream(pbcoreOriginal.getBytes()));

        //get the tvmeter contents
        String tvmeterOriginal = webservice.getDatastreamContents(programUuid, "GALLUP_ORIGINAL");
        //parse it
        TvmeterProgram tvmeterStructure = tvmeterReader.readTVMeterFile(tvmeterOriginal);
        //port the info to the pbcore
        //pbCoreMigrator.addTVMeterStructure(tvmeterStructure);

        //get the shardMetadataContents
        String shardMetadataContents = webservice.getDatastreamContents(shardUuid, "SHARD_METADATA");
        //parse it up
        ShardMetadata shardStructure = deserializeShardMetadata(shardMetadataContents);
        ProgramStructure programStructure = convertShardStructure(shardStructure);
        ProgramBroadcast programBroadcast = makeProgramBroadcast(tvmeterStructure, pbcoreOriginal);

        //NOW START TO CHANGE THE OBJECT

        webservice.markInProgressObject(Arrays.asList(programUuid),"Updating radio/tv datamodel");
        //relations
        for (Relation consistsOfRelation : consistsOfRelations) {
            Relation fileRelation = new Relation();
            fileRelation.setSubject(programUuid);
            fileRelation.setPredicate("http://doms.statsbiblioteket.dk/relations/default/0/1/#hasFile");
            fileRelation.setObject(consistsOfRelation.getObject());
            webservice.addRelation(programUuid,fileRelation,"Updating radio/tv datamodel");
        }

        //webservice.modifyDatastream(programUuid,"PBCORE",pbCoreMigrator.toString(),"Updating radio/tv datamodel");
        webservice.modifyDatastream(programUuid, "PROGRAM_BROADCAST", serializeObject(programBroadcast), "Updating radio/tv datamodel");
        webservice.modifyDatastream(programUuid,"PROGRAM_STRUCTURE",serializeObject(programStructure),"Updating radio/tv datamodel");
        webservice.modifyDatastream(programUuid,"GALLUP_ORIGINAL",serializeObject(tvmeterStructure),"Updating radio/tv datamodel");

        webservice.markPublishedObject(Arrays.asList(programUuid),"Updating radio/tv datamodel");
    }



    public ShardMetadata deserializeShardMetadata(String shardMetadataString) throws JAXBException {
        Document document = DOM.stringToDOM(shardMetadataString, false);
        JAXBElement<ShardMetadata> obj = (JAXBElement<ShardMetadata>) JAXBContext.newInstance(ShardMetadata.class.getPackage().getName()).createUnmarshaller().unmarshal(
                document);
        return obj.getValue();
    }

    public String serializeObject(TvmeterProgram object) throws JAXBException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        QName _TvmeterProgram_QNAME = new QName("", "tvmeterProgram");
        JAXBElement<TvmeterProgram> toSerialize = new JAXBElement<TvmeterProgram>(_TvmeterProgram_QNAME, TvmeterProgram.class, null,object);
        JAXBContext.newInstance(object.getClass().getPackage().getName()).createMarshaller().marshal(toSerialize, result);
        return result.toString();
    }

    public String serializeObject(ProgramStructure object) throws JAXBException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        JAXBElement<ProgramStructure> toSerialize = new JAXBElement<ProgramStructure>(new QName("", "program_structure"), ProgramStructure.class, null, object);
        JAXBContext.newInstance(object.getClass().getPackage().getName()).createMarshaller().marshal(toSerialize, result);
        return result.toString();
    }
    
    public String serializeObject(ProgramBroadcast object) throws JAXBException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        JAXBElement<ProgramBroadcast> toSerialize = new JAXBElement<ProgramBroadcast>(new QName("", "programBroadcast"), ProgramBroadcast.class, null, object);
        JAXBContext.newInstance(object.getClass().getPackage().getName()).createMarshaller().marshal(toSerialize, result);
        return result.toString();
    }

    /**
     * Make a ProgramBroadcast object. If Gallup (tvmeter) data is available use it, otherwise grab the information
     * from PBCore (ritzau). 
     * @throws ParseException 
     */
    public ProgramBroadcast makeProgramBroadcast(TvmeterProgram tvmeter, String pbcore) throws ParseException {
        ProgramBroadcast programBroadcast = new ProgramBroadcast();
        Date startDate, endDate;
        XPathSelector xpath = DOM.createXPathSelector("pb", "http://www.pbcore.org/PBCore/PBCoreNamespace.html");
        Document dom = DOM.stringToDOM(pbcore, true);

        if(tvmeter.getStartDate() != null && tvmeter.getEndDate() != null) {
            DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
            startDate = fmt.parse(tvmeter.getStartDate());
            endDate = fmt.parse(tvmeter.getEndDate());
        } else {
            DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            String startTime = xpath.selectString(dom, "//pb:dateAvailableStart");
            String stopTime =  xpath.selectString(dom, "//pb:dateAvailableEnd");    
            startDate = fmt.parse(startTime);
            endDate = fmt.parse(stopTime);
        }
        
        String channelID = xpath.selectString(dom, "//pb:pbcorePublisher[/pb:publisherRole='channel_name']/pb:publisher");
        
        programBroadcast.setChannelId(channelID);
        programBroadcast.setTimeStart(CalendarUtils.getXmlGregorianCalendar(startDate));
        programBroadcast.setTimeStop(CalendarUtils.getXmlGregorianCalendar(endDate));
        
        return programBroadcast;
        
    }

    public ProgramStructure convertShardStructure(ShardMetadata shardMetadata)
            throws InvalidCredentialsException, InvalidResourceException, MethodFailedException {
        ProgramStructure programStructure = new ProgramStructure();
        if(shardMetadata.getShardStructure() != null) {
            ShardStructure shardStructure = shardMetadata.getShardStructure();
            if(shardStructure.getMissingEnd() != null) {
                MissingEnd missingEnd = new MissingEnd();
                missingEnd.setMissingSeconds(shardStructure.getMissingEnd().getMissingSeconds());
            }
            if(shardStructure.getMissingStart() != null) {
                MissingStart missingStart = new MissingStart();
                missingStart.setMissingSeconds(shardStructure.getMissingStart().getMissingSeconds());
            }
            if(!shardStructure.getHoles().getHole().isEmpty()) {
                Holes holes = new Holes();
                for (Hole shardHole : shardStructure.getHoles().getHole()) {
                    dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.Hole programHole
                            = new dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.Hole();
                    programHole.setHoleLength(shardHole.getHoleLength());
                    programHole.setFile1UUID(webservice.getFileObjectWithURL(shardHole.getFilePath1()));
                    programHole.setFile2UUID(webservice.getFileObjectWithURL(shardHole.getFilePath2()));
                    holes.getHole().add(programHole);
                }
                programStructure.setHoles(holes);
            }
            if(!shardStructure.getOverlaps().getOverlap().isEmpty()) {
                Overlaps overlaps = new Overlaps();
                for (Overlap shardOverlap : shardStructure.getOverlaps().getOverlap()) {
                    dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.Overlap
                            programOverlap
                            = new dk.statsbiblioteket.doms.transformers.shardmigrator.programStructure.autogenerated.Overlap();
                    programOverlap.setOverlapLength(shardOverlap.getOverlapLength());
                    programOverlap.setOverlapType(shardOverlap.getOverlapType());
                    programOverlap.setFile1UUId(webservice.getFileObjectWithURL(shardOverlap.getFilePath1()));
                    programOverlap.setFile2UUID(webservice.getFileObjectWithURL(shardOverlap.getFilePath2()));
                    overlaps.getOverlap().add(programOverlap);
                }
                programStructure.setOverlaps(overlaps);
            }
        }

        return programStructure;
    }

}
