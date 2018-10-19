/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package multicorenlp;

import edu.stanford.nlp.ling.IndexedWord;
import java.util.List;

/**
 *
 * @author wbolduc
 */
public class SVOWithSubs extends SVO{
    private final String whiteList;
    private final String blackList;
    
    public SVOWithSubs(String subject, boolean subNeg, String verb, boolean verbNeg, String object, boolean objNeg, double sentiment, String whiteList, String blackList) {
        super(subject, subNeg, verb, verbNeg, object, objNeg, sentiment);
        this.whiteList = whiteList;
        this.blackList = blackList;
    }

    public String getWhiteList() {
        return whiteList;
    }

    public String getBlackList() {
        return blackList;
    }
    
    
    
}
