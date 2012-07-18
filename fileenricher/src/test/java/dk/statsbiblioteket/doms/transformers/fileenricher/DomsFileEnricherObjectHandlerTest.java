package dk.statsbiblioteket.doms.transformers.fileenricher;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dk.statsbiblioteket.doms.transformers.fileenricher.autogenerated.BroadcastFileDescriptiveMetadataType;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 7/17/12
 * Time: 12:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class DomsFileEnricherObjectHandlerTest {
    private String testMuxFileName = "mux1.1287514800-2010-10-19-21.00.00_1287518400-2010-10-19-22.00.00_dvb1-1.ts";
    private String testMuxStartTime = "1287514800";
    private String testMuxStopTime = "1287518400";
    private String testMuxRecoder = "dvb1-1";
    String testObjectPid;
    
    DomsFileEnricherObjectHandler handler;
    
    @Before
    public void setUp() throws Exception {
        CentralWebservice webservice = new MockWebservice();

        testObjectPid = webservice.newObject(null, null, null);
        webservice.addFileFromPermanentURL(testObjectPid,null,null,"http://bitfinder.statsbiblioteket.dk/bart/"+testMuxFileName,null,null);
        handler = new DomsFileEnricherObjectHandler(null, webservice,null);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testMuxFileNameTransform() throws Exception {
        handler.transform(testObjectPid);
    }


    @Test
    public void testDecodeMuxFilename() throws Exception {
        BroadcastFileDescriptiveMetadataType metadata = handler.decodeFilename(testMuxFileName);
        assertThat(metadata.getRecorder(), is(testMuxRecoder));
        assertThat(metadata.getStartTimeDate(), is(CalendarUtils.getXmlGregorianCalendar(testMuxStartTime)));
        assertThat(metadata.getEndTimeDate(), is(CalendarUtils.getXmlGregorianCalendar(testMuxStopTime)));
    }
    
    @Test
    public void testRadioFileNameTransform() throws Exception {
        
    }
    
    @Test
    public void testMPEGFileNameTransform() throws Exception {
        
    }
}
