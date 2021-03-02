public class SignatureMismatchException extends Exception
{
    public SignatureMismatchException()
    {
        super("Sent and received signatures do not match.");
    }
}