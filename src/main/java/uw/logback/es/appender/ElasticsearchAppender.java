package uw.logback.es.appender;

import okhttp3.Request;
import okhttp3.RequestBody;
import uw.httpclient.http.HttpHelper;
import uw.httpclient.http.HttpInterface;
import uw.httpclient.json.JsonInterfaceHelper;
import uw.httpclient.util.BufferRequestBody;

/**
 * Logback日志接收器
 *
 * @author liliang
 * @since 2018-07-25
 */
public class ElasticsearchAppender<Event> extends AbstractElasticsearchAppender<Event> {

    /**
     * HttpInterface
     */
    private final HttpInterface httpInterface;

    public ElasticsearchAppender() {
        httpInterface = new JsonInterfaceHelper();
    }

    @Override
    protected void append(Event eventObject) {
        if (!isStarted()) {
            return;
        }
        okio.Buffer buffer = new okio.Buffer();
        buffer.write(this.encoder.encode(eventObject));
        postToElasticsearch(buffer);
    }

    private void postToElasticsearch(final okio.Buffer buffer) {
        try {
            httpInterface.requestForObject(new Request.Builder().url(endpoint)
                    .post(BufferRequestBody.create(HttpHelper.JSON_UTF8,buffer))
                    .build(),String.class);
        } catch (final Exception e) {
            addError("IOException while attempting to communicate with Elasticsearch", e);
        }
    }
}
