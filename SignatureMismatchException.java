public class SignatureMismatchException extends Exception
{
    private static final long serialVersionUID = 5401775255958972282L;

    public SignatureMismatchException()
    {
        super("Sent and received signatures do not match.");
    }
}