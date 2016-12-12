package de.mas.jnus.lib;

import java.io.InputStream;
import java.util.Map;

import de.mas.jnus.lib.entities.TMD;
import de.mas.jnus.lib.entities.Ticket;
import de.mas.jnus.lib.entities.content.Content;
import de.mas.jnus.lib.entities.fst.FST;
import de.mas.jnus.lib.implementations.NUSDataProvider;
import de.mas.jnus.lib.utils.StreamUtils;
import de.mas.jnus.lib.utils.cryptography.AESDecryption;

abstract class NUSTitleLoader {
    protected NUSTitleLoader(){
        
    }
        
    public NUSTitle loadNusTitle(NUSTitleConfig config) throws Exception{
        NUSTitle result = new NUSTitle();
        
        NUSDataProvider dataProvider = getDataProvider(config);
        result.setDataProvider(dataProvider);
        dataProvider.setNUSTitle(result);
        
        
        TMD tmd = TMD.parseTMD(dataProvider.getRawTMD());
        result.setTMD(tmd);
                     
        if(tmd == null){
            System.out.println("TMD not found.");
            throw new Exception();
        }
        
        Ticket ticket = config.getTicket();
        if(ticket == null){
            ticket = Ticket.parseTicket(dataProvider.getRawTicket());
        }
        result.setTicket(ticket);        
        System.out.println(ticket);
                
        Content fstContent = tmd.getContentByIndex(0);
        
        
        InputStream fstContentEncryptedStream = dataProvider.getInputStreamFromContent(fstContent, 0);
        if(fstContentEncryptedStream == null){
            
            return null;
        }
        
        byte[] fstBytes = StreamUtils.getBytesFromStream(fstContentEncryptedStream, (int) fstContent.getEncryptedFileSize());
        
        if(fstContent.isEncrypted()){
            AESDecryption aesDecryption = new AESDecryption(ticket.getDecryptedKey(), new byte[0x10]);
            fstBytes = aesDecryption.decrypt(fstBytes);
        }
       
        Map<Integer,Content> contents = tmd.getAllContents();
        
        FST fst = FST.parseFST(fstBytes, contents);        
        result.setFST(fst);
               
        return result;
    }

    protected abstract NUSDataProvider getDataProvider(NUSTitleConfig config);
}
