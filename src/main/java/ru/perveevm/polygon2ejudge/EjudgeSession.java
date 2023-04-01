package ru.perveevm.polygon2ejudge;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import ru.perveevm.polygon2ejudge.exceptions.EjudgeSessionException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
public class EjudgeSession {
    private final String login;
    private final String password;
    private final String cgiBinUrl;

    private CloseableHttpClient client = HttpClients.createDefault();

    private String sid;

    private static final Map<String, Integer> extensionToLangId = Map.of(
            "cpp", 3,
            "py", 23,
            "java", 18,
            "pas", 1
    );

    public EjudgeSession() throws EjudgeSessionException {
        try (InputStream in = ContestManager.class.getClassLoader().getResourceAsStream("app.properties")) {
            Properties properties = new Properties();
            properties.load(in);

            login = properties.getProperty("ejudge.login");
            password = properties.getProperty("ejudge.password");
            cgiBinUrl = properties.getProperty("ejudge.cgiBinUrl");
        } catch (IOException e) {
            throw new EjudgeSessionException("failed to load properties", e);
        }
    }

    private void authenticate(final int contestId) throws IOException, URISyntaxException, EjudgeSessionException {
        client = HttpClients.createDefault();
        HttpPost request = new HttpPost(cgiBinUrl + "/new-master");
        List<NameValuePair> parameters = List.of(
                new BasicNameValuePair("action_2", "Submit"),
                new BasicNameValuePair("contest_id", String.valueOf(contestId)),
                new BasicNameValuePair("locale_id", "0"),
                new BasicNameValuePair("login", login),
                new BasicNameValuePair("password", password),
                new BasicNameValuePair("role", "6")
        );
        request.setEntity(new UrlEncodedFormEntity(parameters, StandardCharsets.UTF_8));
        HttpResponse response = client.execute(request);
        Header[] headers = response.getAllHeaders();
        for (Header header : headers) {
            if (header.getName().equals("Location")) {
                List<NameValuePair> responseParameters = URLEncodedUtils.parse(
                        new URI(header.getValue()), StandardCharsets.UTF_8);
                for (NameValuePair pair : responseParameters) {
                    if (pair.getName().equals("SID")) {
                        sid = pair.getValue();
                        return;
                    }
                }
            }
        }

        throw new EjudgeSessionException("could not parse SID");
    }

    public void submitSolution(final int contestId, final String source, final int problemId, final String extension)
            throws EjudgeSessionException {
        try {
            authenticate(contestId);
        } catch (IOException | URISyntaxException e) {
            throw new EjudgeSessionException("failed to authenticate", e);
        }

        Integer langId = extensionToLangId.getOrDefault(extension, null);
        if (langId == null) {
            return;
        }

        HttpPost request = new HttpPost(cgiBinUrl + "/new-master");
        List<NameValuePair> parameters = List.of(
                new BasicNameValuePair("SID", sid),
                new BasicNameValuePair("action_40", "Send!"),
                new BasicNameValuePair("eoln_type", "0"),
                new BasicNameValuePair("file", ""),
                new BasicNameValuePair("lang_id", String.valueOf(langId)),
                new BasicNameValuePair("problem", String.valueOf(problemId)),
                new BasicNameValuePair("text_form", source)
        );
        request.setEntity(new UrlEncodedFormEntity(parameters, StandardCharsets.UTF_8));

        try {
            client.execute(request);
        } catch (IOException e) {
            throw new EjudgeSessionException("failed to submit solution", e);
        }
    }
}
