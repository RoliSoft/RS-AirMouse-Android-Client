package net.rolisoft.airmouse;

/**
 * Represents a tuple.
 *
 * @param <X> The type of the first data.
 * @param <Y> The type of the second data.
 *
 * @author RoliSoft
 */
public class Tuple<X, Y> {

    /**
     * The first data.
     */
    public final X x;

    /**
     * The second data.
     */
    public final Y y;

    /**
     * Initializes this instance.
     *
     * @param x The first data.
     * @param y The second data.
     */
    public Tuple(X x, Y y)
    {
        this.x = x;
        this.y = y;
    }

}