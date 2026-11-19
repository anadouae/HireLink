package ma.ensat.hirelink.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class DatabaseTargetGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String REALDB_CONFIRMATION = "I_CONFIRM_HIRELINK_REALDB";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate(environment);
    }

    static void validate(Environment environment) {
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        Target target = parse(jdbcUrl);
        boolean realDbProfile = environment.acceptsProfiles(Profiles.of("realdb"));

        if (realDbProfile) {
            if (target == null || !"hirelink".equalsIgnoreCase(target.database()) || !isLocal(target.host())) {
                throw new IllegalStateException("Profile realdb requires a local JDBC URL targeting database hirelink.");
            }
            if (!REALDB_CONFIRMATION.equals(environment.getProperty("hirelink.realdb.confirmation"))) {
                throw new IllegalStateException("Profile realdb requires explicit startup confirmation "
                        + "(hirelink.realdb.confirmation=" + REALDB_CONFIRMATION + ").");
            }
            if (jdbcUrl.toLowerCase(Locale.ROOT).contains("createdatabaseifnotexist=true")) {
                throw new IllegalStateException("Profile realdb must not create databases automatically.");
            }
            return;
        }

        if (target != null && "hirelink".equalsIgnoreCase(target.database()) && isLocal(target.host())) {
            throw new IllegalStateException("Local database hirelink requires profile realdb and explicit confirmation.");
        }
    }

    private static Target parse(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) return null;
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (path == null || path.length() < 2) return null;
            return new Target(uri.getHost(), path.substring(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isLocal(String host) {
        return host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1"));
    }

    private record Target(String host, String database) {}
}
