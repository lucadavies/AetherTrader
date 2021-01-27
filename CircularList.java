import java.util.LinkedList;

/**
 * A minor extension of LinkedList. Must be instatiated with a maximum size which may not be exceeded.
 * Should an element be pushed when the list is at max size, the oldest element is removed to make space.
 */
public class CircularList<E> extends LinkedList<E>
{
    private int maxSize;

    public CircularList(int maxSize)
    {
        super();
        this.maxSize = maxSize;
    }

    public void push(E e)
    {
        if (size() >= maxSize)
        {
            removeLast();
        }
        super.push(e);
    }
}
