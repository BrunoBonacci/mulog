package com.brunobonacci.mulog.core;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flakes are like snowflakes, no two are the same.
 * ------------------------------------------------
 *
 * This is an implementation of a 192-bits unique ids which has the
 * following characteristics:
 *
 *  - **Monotonic IDs**
 *    Every new ID is larger than the previous one. The idea is that the
 *    "happens before" relationship applies to these IDs. If you observe
 *    and Flake and you create a new one, the Flake is going to be larger
 *    than the previous one.
 *    Therefore the following condition should apply for all Flakes
 *    `flake0 < flake1 < flake2 < ... < flakeN`
 *
 *  - **Two components: one time-based (64 bits), one random (128 bits)**
 *    The most significant bits are time based and they use a monotonic
 *    clock with nanosecond resolution. The next 128 bits are randomly
 *    generated.
 *
 *  - **Random-based**
 *    The Random component is built with a PRNG for speed.
 *    It uses 128 full bits, more bits than `java.util.UUID/randomUUID`
 *    which has 6 bits reserved for versioning and type, therefore
 *    effectively only using 122 bits.
 *
 *  - **Homomorphic representation**
 *    Whether you choose to have a bytes representation or string representation
 *    it uses an encoding which maintain the ordering.
 *    Which it means that:
 *    if `flake1 < flake2` then `flake1.toString() < flake2.toString()`
 *    Internally it uses a NON STANDARD base64 encoding to preserve the ordering.
 *    Unfortunately, the standard Base64 encoding doesn't preserve this property
 *    as defined in https://en.wikipedia.org/wiki/Base64.
 *
 *  - **Web-safe string representation**
 *    The string representation uses only characters which are web-safe and can
 *    be put in a URL without the need of URL encoding.
 *
 *  - **Fast**, speed is important so we target under 50 nanosecond for 1 id.
 *    These are the performances measured with Java 1.8.0_232 for the creation
 *    of a new Flake.
 *
 *        ```
 *        Evaluation count : 1570017720 in 60 samples of 26166962 calls.
 *        Execution time mean : 36.429623 ns
 *        Execution time std-deviation : 0.519843 ns
 *        Execution time lower quantile : 35.684111 ns ( 2.5%)
 *        Execution time upper quantile : 37.706974 ns (97.5%)
 *        Overhead used : 2.090673 ns
 *
 *        Found 4 outliers in 60 samples (6.6667 %)
 *        low-severe 4 (6.6667 %)
 *        Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
 *        ```
 *
 *    These are the performances for creating an new Flake and turn it into a string
 *
 *        ```
 *        Evaluation count : 515781720 in 60 samples of 8596362 calls.
 *        Execution time mean : 115.042199 ns
 *        Execution time std-deviation : 1.482372 ns
 *        Execution time lower quantile : 113.117845 ns ( 2.5%)
 *        Execution time upper quantile : 117.949391 ns (97.5%)
 *        Overhead used : 2.090673 ns
 *
 *        Found 3 outliers in 60 samples (5.0000 %)
 *        low-severe  2 (3.3333 %)
 *        low-mild    1 (1.6667 %)
 *        Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
 *        ```
 *
 */
public class Flake implements Comparable<Flake> {

    private final long timePart;
    private final long rand1Part;
    private final long rand2Part;


    private Flake( long time, long rand1, long rand2 ){
        this.timePart  = time;
        this.rand1Part = rand1;
        this.rand2Part = rand2;
    }


    public static final Flake flake(){
        ThreadLocalRandom tl = ThreadLocalRandom.current();
        return new Flake( NanoClock.currentTimeNanos(), tl.nextLong(), tl.nextLong() );
    }


    public byte[] getBytes(){
        byte[] flakeBytes = new byte[24];
        ByteBuffer buf = ByteBuffer.wrap(flakeBytes);
        buf.putLong(timePart).putLong(rand1Part).putLong(rand2Part);
        return flakeBytes;
    }


    public String toString(){
        return Flake.formatFlake( this );
    }


    public long getTimestampNanos(){
        return timePart;
    }


    public long getTimestampMicros(){
        return timePart / 1000;
    }


    public long getTimestampMillis(){
        return timePart / 1000000;
    }


    @Override
    public int compareTo( Flake other ){
        if( other == null )
            return 1;

        int diff = Long.compareUnsigned(this.timePart, other.timePart);
        if( diff != 0 )
            return diff;

        diff = Long.compareUnsigned(this.rand1Part, other.rand1Part);
        if( diff != 0 )
            return diff;

        diff = Long.compareUnsigned(this.rand2Part, other.rand2Part);
        return diff;
    }


    public static final Flake makeFlake( long time, long rand1, long rand2 ){
        return new Flake( time, rand1, rand2);
    }


    /*
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;;                                                                            ;;
    ;;               ----==| S T R I N G   E N C O D I N G |==----                ;;
    ;;                                                                            ;;
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    */

    private static final char[] chars = new char[]{
        '-',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z',
        '_',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
        'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z' };

    private static final int mask00000011 = 3;
    private static final int mask00001111 = 15;
    private static final int mask00111111 = 63;
    private static final int mask11000000 = 192;
    private static final int mask11110000 = 240;
    private static final int mask11111100 = 252;

    /**
     *
     * It uses a custom Base64 encoding to ensure the homomorphic property
     *
     * It also uses a set of characters which can be safely used in a web
     * context without requiring URL encoding as defined by:
     * https://tools.ietf.org/html/rfc3986#section-2.3
     *
     * We use 64 characters because it is a natural power of 2.
     * To represent 64 combination we need 6 bits. Therefore
     * the general strategy to convert the flake into a base 64 string
     * is to grab 6 bits at the time and map them directly their character.
     * In order to do so it is easier to work with bytes instead of long (64bits),
     * because every 3 bytes you have exactly 4 6-bits chunks.
     * the following algorithms takes a flake in bytes representations and
     * it converts it to a custom base 64 encoding:
     *
     * here is how it works:
     *
     * ```
     *           b2           b4
     *        -------       ------
     *  11101011 01110110 11000100 ... (repeat from b1)
     *  ------       -------
     *    b1            b3
     * ```
     *
     * The use of bit masks ans bit shifts is to grab
     * these specific sections of bytes.
     */
    public static final String formatFlake(Flake flakeId){

        byte[] flake = flakeId.getBytes();
        StringBuilder buf = new StringBuilder(32);

        for( int i = 0; i < flake.length; i += 3){
            int b1 = (flake[i]    & mask11111100) >> 2;
            int b2 = ((flake[i]   & mask00000011) << 4) | ((flake[i+1] & mask11110000) >> 4);
            int b3 = ((flake[i+1] & mask00001111) << 2) | ((flake[i+2] & mask11000000) >> 6);
            int b4 = (flake[i+2]  & mask00111111);

            buf.append( chars[b1] ).append( chars[b2] )
               .append( chars[b3] ).append( chars[b4] );
        }

        return buf.toString();
    }

}
