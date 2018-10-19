/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package multicorenlp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 *
 * @author wbolduc
 */
public class CSVTweetWriter {
    private File outFile;
    private CSVPrinter printer;

    //private ArrayList<Thread> queue = new ArrayList<>();
    
    CSVTweetWriter(File outFile) throws IOException
    {
        this.outFile = outFile;
        
        printer = new CSVPrinter(   new BufferedWriter(new FileWriter(outFile)),
                                    CSVFormat.DEFAULT
                                   .withHeader( "user_screen_name",
                                                "id",
                                                "subject",
                                                "subjectNegated",
                                                "verb",
                                                "verbNegated",
                                                "object",
                                                "objectNegated",
                                                "sentimentClass",
                                                "whiteSubSentence",
                                                "blackSubSentence",
                                                "original"));
    }
    
    
    public synchronized void writeTweet(Tweet tweet)
    {
        tweet.getSvos().forEach(svo -> {
            try {
                printer.printRecord(tweet.author,
                                    tweet.tweetID,
                                    svo.getSubject().word,
                                    svo.getSubject().negated,
                                    svo.getVerb().word,
                                    svo.getVerb().negated,
                                    svo.getObject().word,
                                    svo.getObject().negated,
                                    svo.getSentiment(),
                                    ((SVOWithSubs)svo).getWhiteList(),
                                    ((SVOWithSubs)svo).getBlackList(),
                                    tweet.text.text());
            } catch (IOException ex) {
                System.out.println("Could not write tweet to csv");
            }
        });
    }
    
    public void close()
    {
        try {
            printer.close();
        } catch (IOException ex) {
            System.out.println("Already closed");
        }
    }
}
