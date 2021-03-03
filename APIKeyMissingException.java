public class APIKeyMissingException extends Exception
{
    private static final long serialVersionUID = -5410755492215799841L;

    public APIKeyMissingException()
    {
        super("API Keys Missing.");
    }
}