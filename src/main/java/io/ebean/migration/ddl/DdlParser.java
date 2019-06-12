package io.ebean.migration.ddl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses string content into separate SQL/DDL statements.
 */
public class DdlParser {

  /**
   * Break up the sql in reader into a list of statements using the semi-colon and $$ delimiters;
   */
  public List<String> parse(StringReader reader) {

    try {
      BufferedReader br = new BufferedReader(reader);
      StatementsSeparator statements = new StatementsSeparator();

      String s;
      while ((s = br.readLine()) != null) {
        statements.nextLine(s);
      }
      statements.endOfContent();
      return statements.statements;

    } catch (IOException e) {
      throw new DdlRunnerException(e);
    }
  }


  /**
   * Local utility used to detect the end of statements / separate statements.
   * This is often just the semicolon character but for trigger/procedures this
   * detects the $$ demarcation used in the history DDL generation for MySql and
   * Postgres.
   */
  static class StatementsSeparator {

    private static final String EOL = "\n";

    ArrayList<String> statements = new ArrayList<>();

    boolean trimDelimiter;

    boolean inDbProcedure;

    StringBuilder sb = new StringBuilder();

    int quoteCount;

    void lineContainsDollars(String line) {
      if (inDbProcedure) {
        if (trimDelimiter) {
          line = line.replace("$$","");
        }
        endOfStatement(line);
      } else {
        // MySql style delimiter needs to be trimmed/removed
        trimDelimiter = line.equals("delimiter $$");
        if (!trimDelimiter) {
          sb.append(line).append(EOL);
        }
      }
      inDbProcedure = !inDbProcedure;
    }

    void endOfStatement(String line) {
      // end of Db procedure
      sb.append(line);
      statements.add(sb.toString().trim());
      quoteCount = 0;
      sb = new StringBuilder();
    }

    void nextLine(String line) {

      if (line.contains("$$")) {
        lineContainsDollars(line);
        return;
      }

      if (inDbProcedure) {
        sb.append(line).append(EOL);
        return;
      }

      if (sb.length() == 0 && (line.isEmpty() || line.startsWith("--"))) {
        // ignore leading empty lines and sql comments
        return;
      }

      quoteCount += countQuotes(line);
      if (hasOddQuotes()) {
        // must continue
        sb.append(line).append(EOL);
        return;
      }

      int semiPos = line.lastIndexOf(';');
      if (semiPos == -1) {
        sb.append(line).append(EOL);

      } else if (semiPos == line.length() - 1) {
        // semicolon at end of line
        endOfStatement(line);

      } else {
        // semicolon in middle of line
        String remaining = line.substring(semiPos + 1).trim();
        if (!remaining.startsWith("--")) {
          // remaining not an inline sql comment so keep going ...
          sb.append(line).append(EOL);
          return;
        }

        String preSemi = line.substring(0, semiPos + 1);
        endOfStatement(preSemi);
      }
    }

    /**
     * Return true if the count of quotes is odd.
     */
    private boolean hasOddQuotes() {
      return quoteCount % 2 == 1;
    }

    /**
     * Return the count of single quotes in the content.
     */
    private int countQuotes(String content) {
      int count = 0;
      for (int i = 0; i < content.length(); i++) {
        if (content.charAt(i) == '\'') {
          count++;
        }
      }
      return count;
    }

    /**
     * Append trailing non-terminated content as an extra statement.
     */
    void endOfContent() {
      String remaining = sb.toString().trim();
      if (remaining.length() > 0) {
        statements.add(remaining);
        sb = new StringBuilder();
      }
    }
  }
}
