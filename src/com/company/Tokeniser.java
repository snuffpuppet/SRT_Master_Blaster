package com.company;

import jdk.nashorn.internal.runtime.ParserException;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adam on 14/01/17.
 */
public class Tokeniser
{
    private class TokenInfo {
        public final Pattern regex;
        public final int token;

        public TokenInfo(Pattern regex, int token) {
            super();
            this.regex = regex;
            this.token = token;
        }
    }

    public class Token {
        public final int token;
        public final String sequence;

        public Token(int token, String sequence) {
            super();
            this.token = token;
            this.sequence = sequence;
        }
    }

    private LinkedList<TokenInfo> tokenInfos;
    private LinkedList<Token> tokens;

    public Tokeniser() {
        tokenInfos = new LinkedList<TokenInfo>();
        tokens = new LinkedList<Token>();
    }

    public void add(String regex, int token) {
        tokenInfos.add(
                new TokenInfo(
                        Pattern.compile("^("+regex+")"), token));
    }

    private String removeUnprintable(String s) {
        Pattern regex = Pattern.compile("^\\S*");
        Matcher m = regex.matcher(s);
        if (m.find()) {
            s = m.replaceFirst("");
        }

        return s;
    }

    public void tokenise(String str) {
        String s = new String(str);
        tokens.clear();

        //s = removeUnprintable(s);

        while (!s.equals("")) {
            boolean match = false;


            for (TokenInfo info : tokenInfos) {
                Matcher m = info.regex.matcher(s.trim());
                if (m.find()) {
                    match = true;

                    String tok = m.group().trim();
                    tokens.add(new Token(info.token, tok));

                    s = m.replaceFirst("");
                    break;
                }
            }
            if (!match) throw new ParserException(
                    "Unexpected character in input: "+s);
        }

    }

    public LinkedList<Token> getTokens() {
        return tokens;
    }

}

