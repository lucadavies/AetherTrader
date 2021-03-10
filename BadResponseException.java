public class BadResponseException extends Exception
{
    private static final long serialVersionUID = -2515762053538383721L;
    
    /**
     * Non-200 HTTP code
     */
    private int code;

    public BadResponseException(int code)
    {
        super("Non-200 status code received.");
    }

    public int getCode()
    {
        return code;
    }
}