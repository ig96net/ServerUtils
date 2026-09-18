package net.frankheijden.serverutils.common.entities.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GitHubResponse implements AutoCloseable {

    private final HttpURLConnection connection;
    private final GitHubRateLimit rateLimit;

    public static GitHubResponse from(HttpURLConnection connection) {
        return new GitHubResponse(connection, GitHubRateLimit.from(connection));
    }

    public InputStream getStream() throws IOException {
        int res = connection.getResponseCode();
        return (res >= 200 && res <= 299) ? connection.getInputStream() : connection.getErrorStream();
    }

    @Override
    public void close() {
        connection.disconnect();
    }
}
