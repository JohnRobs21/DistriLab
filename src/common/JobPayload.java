package common;

import java.io.Serializable;


public class JobPayload implements Serializable{
    private static final long serialVersionUID = 1L;
    private int startRange;
    private int endRange;
    private int[] numberArray;

    //Constructor for Range-based tasks (PRIMESUM, PRIMECOUNT)
    public JobPayload(int startRange, int endRange){
        this.startRange = startRange;
        this.endRange = endRange;
    }

    //Constructor for Array-based tasks (MAX)
    public JobPayload(int[] numberArray){
        this.numberArray = numberArray;
    }

    public int getStartRange(){
        return startRange;
    }

    public int getEndRange(){
        return endRange;
    }

    public int[] getNumberArray(){
        return numberArray;
    }

}
