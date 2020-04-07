package de.stephanlindauer.criticalmaps.handler;

import android.os.AsyncTask;

import org.json.JSONException;

import java.text.ParseException;

import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.fragments.TwitterFragment;
import de.stephanlindauer.criticalmaps.model.TwitterModel;
import de.stephanlindauer.criticalmaps.vo.Endpoints;
import de.stephanlindauer.criticalmaps.vo.ResultType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class TwitterGetHandler extends AsyncTask<Void, Void, ResultType> {

    //dependencies
    private final TwitterModel twitterModel = App.components().twitterModel();
    private final TwitterFragment twitterFragment;

    private String responseString = "";

    public TwitterGetHandler(TwitterFragment twitterFragment) {
        this.twitterFragment = twitterFragment;
    }

    @Override
    protected void onPreExecute() {
        twitterFragment.setRefreshing(true);
    }

    @Override
    protected ResultType doInBackground(Void... params) {
        final Request request = new Request.Builder().url(Endpoints.GET_TWITTER).get().build();

        final OkHttpClient httpClient = App.components().okHttpClient();
        try {
            final Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                //noinspection ConstantConditions "Returns a non-null value if this response was [...] returned from Call.execute()."
                responseString = response.body().string();
                return ResultType.SUCCEEDED;
            }
        } catch (Exception ignored) {
        }

        return ResultType.FAILED;
    }

    @Override
    protected void onPostExecute(ResultType resultType) {
        if (!twitterFragment.isAdded()) {
            return;
        }

        twitterFragment.setRefreshing(false);

        if (resultType == ResultType.FAILED) {
            twitterFragment.showErrorMessage();
        } else {
            try {
                twitterModel.setTweetsFromJsonString(responseString);
                twitterFragment.displayNewData();
            } catch (JSONException | ParseException e) {
                Timber.e(e);
            }
        }
    }
}
