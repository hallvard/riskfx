package no.hal.risk.fx;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RiskFxApp {

  // @Inject
  // FXMLLoader fxmlLoader;

  // public void start(@Observes final FxPostStartupEvent startupEvent) {

  //   Stage stage = startupEvent.getPrimaryStage();

  //   stage.setOnCloseRequest(_ -> {
  //       Platform.exit();
  //       System.exit(0);
  //   });

  //   try {
  //       InputStream fxml = this.getClass().getResourceAsStream("/fxviews/MapView.fxml");
  //       Parent fxmlParent = this.fxmlLoader.load(fxml);

  //       Scene scene = new Scene(fxmlParent, 1000, 800);
  //       stage.setScene(scene);
  //       stage.setTitle("RiskFx");
  //       stage.show();

  //   } catch (Exception e) {
  //       throw new RuntimeException(e);
  //   }
  // }
}
