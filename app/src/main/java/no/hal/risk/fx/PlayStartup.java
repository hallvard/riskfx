package no.hal.risk.fx;

import io.quarkiverse.fx.FxPostStartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.HashMap;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Shows the window for playing and watching, {@link PlayApp}, when the application has started, e.g. with {@code mvn quarkus:dev}.
 * The trained networks are in the application, and other ones can be used with the
 * configuration properties {@code riskfx.ai2} and {@code riskfx.ai4}, with the files.
 */
@ApplicationScoped
public class PlayStartup {

  private static final Logger logger = Logger.getLogger(PlayStartup.class);

  void start(@Observes FxPostStartupEvent startupEvent) {
    var config = ConfigProvider.getConfig();
    var options = new HashMap<String, String>();
    config.getOptionalValue("riskfx.ai2", String.class).ifPresent(file -> options.put("ai2", file));
    config.getOptionalValue("riskfx.ai4", String.class).ifPresent(file -> options.put("ai4", file));
    logger.infof("Showing the window for playing and watching, with the options %s", options);
    new PlayApp().show(startupEvent.getPrimaryStage(), options);
  }
}
