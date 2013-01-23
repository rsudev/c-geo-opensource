package cgeo.geocaching.gcvote;

import cgeo.geocaching.Settings;
import cgeo.geocaching.cgCache;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.utils.LeastRecentlyUsedMap;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MatcherWrapper;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class GCVote {
    private static final Pattern patternLogIn = Pattern.compile("loggedIn='([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternGuid = Pattern.compile("cacheId='([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternRating = Pattern.compile("voteAvg='([0-9.]+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternVotes = Pattern.compile("voteCnt='([0-9]+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternVote = Pattern.compile("voteUser='([0-9.]+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternVoteElement = Pattern.compile("<vote ([^>]+)>", Pattern.CASE_INSENSITIVE);

    private static final int MAX_CACHED_RATINGS = 1000;
    private static LeastRecentlyUsedMap<String, GCVoteRating> ratingsCache = new LeastRecentlyUsedMap.LruCache<String, GCVoteRating>(MAX_CACHED_RATINGS);

    /**
     * Get user rating for a given guid or geocode. For a guid first the ratings cache is checked
     * before a request to gcvote.com is made.
     *
     * @param guid
     * @param geocode
     * @return
     */
    public static GCVoteRating getRating(String guid, String geocode) {
        if (StringUtils.isNotBlank(guid) && ratingsCache.containsKey(guid)) {
            return ratingsCache.get(guid);
        }

        final Map<String, GCVoteRating> ratings = getRating(singletonOrNull(guid), singletonOrNull(geocode));
        return MapUtils.isNotEmpty(ratings) ? ratings.values().iterator().next() : null;
    }

    private static List<String> singletonOrNull(final String item) {
        return StringUtils.isNotBlank(item) ? Collections.singletonList(item) : null;
    }

    /**
     * Get user ratings from gcvote.com
     *
     * @param guids
     * @param geocodes
     * @return
     */
    public static Map<String, GCVoteRating> getRating(List<String> guids, List<String> geocodes) {
        if (guids == null && geocodes == null) {
            return null;
        }

        final Map<String, GCVoteRating> ratings = new HashMap<String, GCVoteRating>();

        try {
            final Parameters params = new Parameters();
            if (Settings.isLogin()) {
                final ImmutablePair<String, String> login = Settings.getGCvoteLogin();
                if (login != null) {
                    params.put("userName", login.left, "password", login.right);
                }
            }
            if (guids != null && !guids.isEmpty()) {
                params.put("cacheIds", StringUtils.join(guids.toArray(), ','));
            } else {
                params.put("waypoints", StringUtils.join(geocodes.toArray(), ','));
            }
            params.put("version", "cgeo");
            final String page = Network.getResponseData(Network.getRequest("http://gcvote.com/getVotes.php", params));
            if (page == null) {
                return null;
            }

            final MatcherWrapper matcherVoteElement = new MatcherWrapper(patternVoteElement, page);
            while (matcherVoteElement.find()) {
                String voteData = matcherVoteElement.group(1);
                if (voteData == null) {
                    continue;
                }

                String guid = null;
                try {
                    final MatcherWrapper matcherGuid = new MatcherWrapper(patternGuid, voteData);
                    if (matcherGuid.find()) {
                        if (matcherGuid.groupCount() > 0) {
                            guid = matcherGuid.group(1);
                        }
                    }
                } catch (Exception e) {
                    Log.w("GCVote.getRating: Failed to parse guid");
                }
                if (guid == null) {
                    continue;
                }

                boolean loggedIn = false;
                try {
                    final MatcherWrapper matcherLoggedIn = new MatcherWrapper(patternLogIn, page);
                    if (matcherLoggedIn.find()) {
                        if (matcherLoggedIn.groupCount() > 0) {
                            if (matcherLoggedIn.group(1).equalsIgnoreCase("true")) {
                                loggedIn = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w("GCVote.getRating: Failed to parse loggedIn");
                }

                float rating = 0;
                try {
                    final MatcherWrapper matcherRating = new MatcherWrapper(patternRating, voteData);
                    if (matcherRating.find()) {
                        rating = Float.parseFloat(matcherRating.group(1));
                    }
                } catch (Exception e) {
                    Log.w("GCVote.getRating: Failed to parse rating");
                }
                if (rating <= 0) {
                    continue;
                }

                int votes = -1;
                try {
                    final MatcherWrapper matcherVotes = new MatcherWrapper(patternVotes, voteData);
                    if (matcherVotes.find()) {
                        votes = Integer.parseInt(matcherVotes.group(1));
                    }
                } catch (Exception e) {
                    Log.w("GCVote.getRating: Failed to parse vote count");
                }
                if (votes < 0) {
                    continue;
                }

                float myVote = 0;
                if (loggedIn) {
                    try {
                        final MatcherWrapper matcherVote = new MatcherWrapper(patternVote, voteData);
                        if (matcherVote.find()) {
                            myVote = Float.parseFloat(matcherVote.group(1));
                        }
                    } catch (Exception e) {
                        Log.w("GCVote.getRating: Failed to parse user's vote");
                    }
                }

                if (StringUtils.isNotBlank(guid)) {
                    GCVoteRating gcvoteRating = new GCVoteRating(rating, votes, myVote);
                    ratings.put(guid, gcvoteRating);
                    ratingsCache.put(guid, gcvoteRating);
                }
            }
        } catch (Exception e) {
            Log.e("GCVote.getRating", e);
        }

        return ratings;
    }

    /**
     * Transmit user vote to gcvote.com
     *
     * @param cache
     * @param vote
     * @return
     */
    public static boolean setRating(cgCache cache, double vote) {
        if (!cache.supportsGCVote()) {
            return false;
        }
        String guid = cache.getGuid();
        if (StringUtils.isBlank(guid)) {
            return false;
        }
        if (vote <= 0.0 || vote > 5.0) {
            return false;
        }

        final ImmutablePair<String, String> login = Settings.getGCvoteLogin();
        if (login == null) {
            return false;
        }

        final Parameters params = new Parameters(
                "userName", login.left,
                "password", login.right,
                "cacheId", guid,
                "voteUser", String.format("%.1f", vote).replace(',', '.'),
                "version", "cgeo");

        final String result = Network.getResponseData(Network.getRequest("http://gcvote.com/setVote.php", params));

        return result.trim().equalsIgnoreCase("ok");
    }

    public static void loadRatings(ArrayList<cgCache> caches) {
        if (!Settings.isRatingWanted()) {
            return;
        }

        final ArrayList<String> guids = new ArrayList<String>(caches.size());
        for (final cgCache cache : caches) {
            String guid = cache.getGuid();
            if (StringUtils.isNotBlank(guid)) {
                guids.add(guid);
            }
        }

        if (guids.isEmpty()) {
            return;
        }

        try {
            final Map<String, GCVoteRating> ratings = GCVote.getRating(guids, null);

            if (MapUtils.isNotEmpty(ratings)) {
                // save found cache coordinates
                for (cgCache cache : caches) {
                    if (ratings.containsKey(cache.getGuid())) {
                        GCVoteRating rating = ratings.get(cache.getGuid());

                        cache.setRating(rating.getRating());
                        cache.setVotes(rating.getVotes());
                        cache.setMyVote(rating.getMyVote());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("GCvote.loadRatings", e);
        }
    }
}
