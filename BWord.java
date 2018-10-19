/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package multicorenlp;

/**
 *
 * @author wbolduc
 */
public class BWord {
    public final String word;
    public final Boolean negated;

    public BWord(String word, Boolean negated) {
        this.word = word;
        this.negated = negated;
    }

    @Override
    public String toString() {
        if (negated)
            return "not-" + word;
        return word;
    }
    
    
}
