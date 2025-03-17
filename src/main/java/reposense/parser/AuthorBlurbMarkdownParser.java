package reposense.parser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

import reposense.model.Author;
import reposense.model.AuthorBlurbMap;
import reposense.model.BlurbMap;
import reposense.model.RepoBlurbMap;
import reposense.parser.exceptions.InvalidMarkdownException;

/**
 * Parses the Markdown file and retrieve mappings from author to blurbs from the blurbs
 */
public class AuthorBlurbMarkdownParser extends MarkdownParser<AuthorBlurbMap> implements BlurbMarkdownParser {
    public static final Pattern DELIMITER = Pattern.compile("<!--author-->(.*)");
    public static final String DEFAULT_BLURB_FILENAME = "author-blurbs.md";

    public static final class AuthorRecord {
        private final String authorGitId;
        /**
         * The next position to read from the markdown file.
         */
        private final int nextPosition;

        public AuthorRecord(String authorGitId, int nextPosition) {
            this.authorGitId = authorGitId;
            this.nextPosition = nextPosition;
        }

        public String getAuthorGitId() {
            return authorGitId;
        }

        public int getNextPosition() {
            return nextPosition;
        }
    }

    private static final class BlurbsRecord {
        private final List<String> blurb;
        private final int nextPosition;

        public BlurbsRecord(List<String> blurb, int nextPosition) {
            this.blurb = blurb;
            this.nextPosition = nextPosition;
        }

        public List<String> getBlurb() {
            return blurb;
        }

        public int getNextPosition() {
            return nextPosition;
        }
    }

    public AuthorBlurbMarkdownParser(Path markdownPath) throws FileNotFoundException {
        super(markdownPath);
    }

    /**
     * Parses the markdown file containing the author git id to blurb mapping and returns a
     * {@code AuthorBlurbMap} containing the mappings between the author git id and blurbs.
     *
     * @return {@code AuthorBlurbMap} object.
     * @throws IOException if there are any issues opening or parsing the {@code author-blurbs.md} file.
     */
    @Override
    public AuthorBlurbMap parse() throws IOException, InvalidMarkdownException {
        logger.log(Level.INFO, "Parsing Authors Blurbs...");
        // Read all the lines first
        List<String> mdlines = Files.readAllLines(markdownPath);

        // If the files is empty, throw exception and let the adder handle
        if (mdlines.isEmpty()) {
            throw new InvalidMarkdownException("Empty author-blurbs.md file");
        }

        // Prepare the blurb map
        AuthorBlurbMap authorBlurbMap = new AuthorBlurbMap();

        // Define temporary local variables to track blurbs
        String authorGitId = "";
        StringBuilder blurb = new StringBuilder();
        int counter = 0;

        while (counter < mdlines.size()) {
            // Extract the author git id first (should be at the first line)
            AuthorRecord authorRecord = this.getAuthorRecord(mdlines, counter);

            // if delimiter is the last non-blank line, null is returned
            if (authorRecord == null) {
               break;
            }

            authorGitId = authorRecord.getAuthorGitId();
            counter = authorRecord.getNextPosition();

            // Extract the blurb content
            BlurbsRecord blurbsRecord = this.getBlurbRecord(mdlines, counter);
            List<String> blurbExtracted = blurbsRecord.getBlurb();
            for (String line : blurbExtracted) {
                blurb.append(line);
            }

            counter = blurbsRecord.getNextPosition();

            // Add the recorded entry into the blurb map
            // Strip trailing /n
            authorBlurbMap.withRecord(authorGitId, blurb.toString().stripTrailing());
            blurb.setLength(0);
        }

        // return the built author blurb map instance
        logger.log(Level.INFO, "Authors Blurbs parsed successfully.");
        return authorBlurbMap;
    }

    /**
     * Extracts the author git id from the markdown file.
     *
     * @param lines the list of lines in the markdown file
     * @param position the current position in the markdown file
     *
     * @return the {@code AuthorRecord} containing the author git id and the next position to read from the markdown file
     */
    private AuthorRecord getAuthorRecord(List<String> lines, int position) throws InvalidMarkdownException {
        // Extract the author git id
        String authorGitId = "";

        while (authorGitId.length() == 0) {
            // Check is deliminiter is found
            if (position >= lines.size()) {
                return null;
            }

            authorGitId = lines.get(position++).strip();
        }

        return new AuthorRecord(authorGitId, position);
    }

    private BlurbsRecord getBlurbRecord(List<String> lines, int position) {
        int lineSize = lines.size();
        int posCounter = position;
        List<String> blurbs = new ArrayList<>();

        while (posCounter < lineSize) {
            String currLine = lines.get(posCounter);

            if (RepoBlurbMarkdownParser.DELIMITER.matcher(currLine).matches()) {
                break;
            } else {
                currLine += "\n";
                blurbs.add(currLine);
            }

            posCounter++;
        }

        return new BlurbsRecord(blurbs, posCounter);
    }
}