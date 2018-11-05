/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package multicorenlp;

import java.util.Objects;

/**
 *
 * @author wbolduc
 */
public class SVO {
    private final BWord subject;
    private final BWord verb;
    private final BWord object;
    
    private final double sentiment;
    
    public SVO(String subject, boolean subNeg, String verb, boolean verbNeg, String object, boolean objNeg, double sentiment) {
        this.subject    = new BWord(subject, subNeg);
        this.verb       = new BWord(verb, verbNeg);
        this.object     = new BWord(object, objNeg);
        
        this.sentiment = sentiment;
    }
    
    public BWord getSubject() {
        return subject;
    }

    public BWord getVerb() {
        return verb;
    }

    public BWord getObject() {
        return object;
    }
    
    public double getSentiment() {
        return sentiment;
    }

    public boolean isSubNeg() {
        return subject.negated;
    }

    public boolean isVerbNeg() {
        return verb.negated;
    }

    public boolean isObjNeg() {
        return object.negated;
    }
    
    

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SVO{subject=");
        sb.append(subject.toString());
        sb.append(", verb=");
        sb.append(verb.toString());
        sb.append(", object=");
        sb.append(object.toString());
        sb.append(", sentiment=");
        /*
        switch(sentimentClass)
        {
            case 0:
                sb.append("Very Negative");
                break;
            case 1:
                sb.append("Negative");
                break;
            case 2:
                sb.append("Neutral");
                break;
            case 3:
                sb.append("Positive");
                break;
            case 4:
                sb.append("Very Positive");
                break;
        }
        */
        sb.append(sentiment);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.subject);
        hash = 89 * hash + Objects.hashCode(this.verb);
        hash = 89 * hash + Objects.hashCode(this.object);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SVO other = (SVO) obj;
        if (!Objects.equals(this.subject, other.subject)) {
            return false;
        }
        if (!Objects.equals(this.verb, other.verb)) {
            return false;
        }
        if (!Objects.equals(this.object, other.object)) {
            return false;
        }
        return true;
    }






    
}
