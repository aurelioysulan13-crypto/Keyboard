
package com.custom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Word suggestions, next-word prediction, auto-correct and on-device word learning. */
final class Suggester {

    private static final String[][] NEXT = {
            {"i", "am", "have", "will"},
            {"i'm", "not", "going", "sorry"},
            {"you", "are", "can", "have"},
            {"thank", "you"},
            {"thanks", "for", "so", "a"},
            {"how", "are", "to", "do"},
            {"what", "is", "are", "do"},
            {"where", "is", "are", "do"},
            {"are", "you", "we", "they"},
            {"is", "it", "this", "there"},
            {"do", "you", "not", "we"},
            {"don't", "know", "think", "want"},
            {"can", "you", "i", "we"},
            {"see", "you", "the", "if"},
            {"good", "morning", "night", "luck"},
            {"have", "a", "to", "you"},
            {"want", "to", "a", "you"},
            {"going", "to", "on", "home"},
            {"in", "the", "a", "my"},
            {"of", "the", "a", "course"},
            {"to", "be", "the", "do"},
            {"it", "is", "was", "will"},
            {"let", "me", "us", "you"},
            {"please", "let", "send", "help"},
            {"happy", "birthday", "to", "new"},
            {"for", "the", "a", "you"},
            {"on", "the", "my", "a"},
            {"this", "is", "will", "one"},
            {"that", "is", "was", "you"},
            {"the", "same", "best", "first"}
    };

    private static final String[][] FIXES = {
            {"i", "I"}, {"im", "I'm"}, {"i'm", "I'm"}, {"ive", "I've"}, {"i've", "I've"},
            {"i'll", "I'll"}, {"i'd", "I'd"}, {"dont", "don't"}, {"cant", "can't"},
            {"wont", "won't"}, {"didnt", "didn't"}, {"doesnt", "doesn't"}, {"isnt", "isn't"},
            {"arent", "aren't"}, {"wasnt", "wasn't"}, {"werent", "weren't"},
            {"couldnt", "couldn't"}, {"wouldnt", "wouldn't"}, {"shouldnt", "shouldn't"},
            {"havent", "haven't"}, {"hasnt", "hasn't"}, {"youre", "you're"},
            {"theyre", "they're"}, {"thats", "that's"}, {"whats", "what's"},
            {"teh", "the"}, {"recieve", "receive"}, {"definately", "definitely"},
            {"seperate", "separate"}, {"occured", "occurred"}, {"becuase", "because"},
            {"alot", "a lot"}, {"thier", "their"}, {"wich", "which"}, {"freind", "friend"},
            {"adress", "address"}, {"tommorow", "tomorrow"}, {"untill", "until"},
            {"wierd", "weird"}, {"beleive", "believe"}
    };

    private final Context appContext;
    private final ArrayList<String> words = new ArrayList<>();
    private final HashSet<String> wordSet = new HashSet<>();
    private final HashSet<String> learned = new HashSet<>();
    private final HashMap<String, Integer> seen = new HashMap<>();
    private final HashMap<String, String[]> next = new HashMap<>();
    private final HashMap<String, String> fixes = new HashMap<>();
    private volatile boolean ready = false;

    Suggester(Context ctx) {
        appContext = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        for (String[] n : NEXT) {
            next.put(n[0], Arrays.copyOfRange(n, 1, n.length));
        }
        for (String[] f : FIXES) {
            fixes.put(f[0], f[1]);
        }
        Set<String> saved = Prefs.get(appContext).getStringSet(Prefs.K_LEARNED, null);
        if (saved != null) {
            learned.addAll(saved);
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loadWords();
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void loadWords() {
        ArrayList<String> list = new ArrayList<>();
        HashSet<String> set = new HashSet<>();
        try {
            InputStream in = appContext.getAssets().open("words.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String w = line.trim().toLowerCase(Locale.ROOT);
                if (!w.isEmpty() && set.add(w)) {
                    list.add(w);
                }
            }
            r.close();
        } catch (Exception ignored) {
            // No dictionary? The keyboard still works, just without completions.
        }
        words.addAll(list);
        wordSet.addAll(set);
        ready = true;
    }

    /** Typed word first, then up to (max - 1) completions. */
    List<String> suggest(String typed, int max) {
        ArrayList<String> out = new ArrayList<>();
        if (typed.isEmpty()) {
            return out;
        }
        out.add(typed);
        if (!ready) {
            return out;
        }
        String p = typed.toLowerCase(Locale.ROOT);
        for (String w : learned) {
            if (out.size() >= max) break;
            if (w.length() > p.length() && w.startsWith(p)) {
                addUnique(out, matchCase(w, typed));
            }
        }
        for (int i = 0; i < words.size() && out.size() < max; i++) {
            String w = words.get(i);
            if (w.length() > p.length() && w.startsWith(p)) {
                addUnique(out, matchCase(w, typed));
            }
        }
        return out;
    }

    /** Guesses for the next word, given the previous word (lowercase). */
    List<String> nextWords(String prev) {
        ArrayList<String> out = new ArrayList<>();
        String[] n = next.get(prev);
        if (n != null) {
            for (String s : n) {
                if (out.size() < 3) out.add(s);
            }
        }
        String[] defaults = {"I", "the", "to"};
        for (String d : defaults) {
            if (out.size() < 3 && !out.contains(d)) out.add(d);
        }
        return out;
    }

    /** Returns the corrected form of a word, or null if it needs no correction. */
    String correction(String w) {
        if (w.isEmpty()) return null;
        String c = fixes.get(w.toLowerCase(Locale.ROOT));
        if (c == null) return null;
        String result = c;
        if (w.length() > 1 && w.equals(w.toUpperCase(Locale.ROOT))) {
            result = c.toUpperCase(Locale.ROOT);
        } else if (Character.isUpperCase(w.charAt(0)) && !Character.isUpperCase(c.charAt(0))) {
            result = Character.toUpperCase(c.charAt(0)) + c.substring(1);
        }
        return result.equals(w) ? null : result;
    }

    /** Remembers a word once it has been typed twice and isn't in the dictionary. */
    void learn(String word) {
        if (!ready) return;
        String w = word.toLowerCase(Locale.ROOT);
        if (w.length() < 3 || w.length() > 24) return;
        if (wordSet.contains(w) || learned.contains(w) || fixes.containsKey(w)) return;
        Integer prev = seen.get(w);
        int c = (prev == null) ? 1 : prev + 1;
        if (c >= 2) {
            seen.remove(w);
            if (learned.size() >= 2000) {
                Iterator<String> it = learned.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            learned.add(w);
            persist();
        } else {
            seen.put(w, c);
        }
    }

    void clearLearned() {
        learned.clear();
        seen.clear();
    }

    private void persist() {
        SharedPreferences.Editor e = Prefs.get(appContext).edit();
        e.putStringSet(Prefs.K_LEARNED, new HashSet<>(learned));
        e.apply();
    }

    private static void addUnique(List<String> list, String s) {
        if (!list.contains(s)) list.add(s);
    }

    private static String matchCase(String w, String typed) {
        if (w.equals("i") || w.startsWith("i'")) {
            return "I" + w.substring(1);
        }
        if (typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT))) {
            return w.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(typed.charAt(0))) {
            return Character.toUpperCase(w.charAt(0)) + w.substring(1);
        }
        return w;
    }
}
