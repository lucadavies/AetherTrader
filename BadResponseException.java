public class BadResponseException extends Exception
{
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