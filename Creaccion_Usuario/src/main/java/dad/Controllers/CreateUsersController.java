package dad.Controllers;

import com.fasterxml.jackson.databind.*;
import dad.Models.*;
import dad.Models.TruenasConnection;
import javafx.event.*;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.net.*;
import java.util.*;

public class CreateUsersController implements Initializable {


    @FXML
    private Button cancelButton;

    @FXML
    private Button clearButton;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField fullNameField;

    @FXML
    private CheckBox groupCreateCheckbox;

    @FXML
    private CheckBox passwordDisabledCheckbox;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox smbCheckbox;

    @FXML
    private Button submitButton;

    @FXML
    private TextField usernameField;


    private TruenasConnection truenasConnection;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
            truenasConnection = new TruenasConnection();
        passwordDisabledCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean disablePassword = newVal;

            passwordField.clear();
            confirmPasswordField.clear();

            passwordField.setDisable(disablePassword);
            confirmPasswordField.setDisable(disablePassword);
        });
    }

    @FXML
    void onCancelarAction(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmación");
        alert.setHeaderText("¿Estás seguro que deseas cancelar?");
        alert.setContentText("Se perderán los datos ingresados.");

        Optional<ButtonType> resultado = alert.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.close();
        }
    }

    @FXML
    void onLimpiarAction(ActionEvent event) {
        fullNameField.clear();
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();

        passwordDisabledCheckbox.setSelected(false);
        groupCreateCheckbox.setSelected(false);
        smbCheckbox.setSelected(false);

        fullNameField.requestFocus();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Formulario limpiado");
        alert.setHeaderText(null);
        alert.setContentText("Todos los campos han sido reiniciados.");
        alert.showAndWait();

    }

    @FXML
    void onSubmitAction(ActionEvent event) {
        String fullName = fullNameField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (fullName.isEmpty() || username.isEmpty()) {
            showError("El nombre completo y el nombre de usuario son obligatorios.");
            return;
        }

        if (!passwordDisabledCheckbox.isSelected()) {
            if (password.isEmpty() || confirmPassword.isEmpty()) {
                showError("Debe ingresar y confirmar la contraseña.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                showError("Las contraseñas no coinciden.");
                return;
            }
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("full_name", fullName);
        userData.put("username", username);

        if (!passwordDisabledCheckbox.isSelected()) {
            userData.put("password", password);
        } else {
            userData.put("password_disabled", true);
        }

        userData.put("smb", smbCheckbox.isSelected());
        userData.put("group_create", groupCreateCheckbox.isSelected());

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(userData);

            boolean success = truenasConnection.createUserFromJson(jsonPayload);

            if (success) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Usuario creado");
                alert.setHeaderText(null);
                alert.setContentText("El usuario ha sido creado exitosamente.");
                alert.showAndWait();

                onLimpiarAction(null);
            }

        } catch (Exception e) {
            showError("Error al crear el usuario: " + e.getMessage());
            e.printStackTrace();
        }

    }





    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    public void setCancelButton(Button cancelButton) {
        this.cancelButton = cancelButton;
    }

    public Button getClearButton() {
        return clearButton;
    }

    public void setClearButton(Button clearButton) {
        this.clearButton = clearButton;
    }

    public PasswordField getConfirmPasswordField() {
        return confirmPasswordField;
    }

    public void setConfirmPasswordField(PasswordField confirmPasswordField) {
        this.confirmPasswordField = confirmPasswordField;
    }

    public TextField getFullNameField() {
        return fullNameField;
    }

    public void setFullNameField(TextField fullNameField) {
        this.fullNameField = fullNameField;
    }

    public CheckBox getGroupCreateCheckbox() {
        return groupCreateCheckbox;
    }

    public void setGroupCreateCheckbox(CheckBox groupCreateCheckbox) {
        this.groupCreateCheckbox = groupCreateCheckbox;
    }

    public CheckBox getPasswordDisabledCheckbox() {
        return passwordDisabledCheckbox;
    }

    public void setPasswordDisabledCheckbox(CheckBox passwordDisabledCheckbox) {
        this.passwordDisabledCheckbox = passwordDisabledCheckbox;
    }

    public PasswordField getPasswordField() {
        return passwordField;
    }

    public void setPasswordField(PasswordField passwordField) {
        this.passwordField = passwordField;
    }

    public CheckBox getSmbCheckbox() {
        return smbCheckbox;
    }

    public void setSmbCheckbox(CheckBox smbCheckbox) {
        this.smbCheckbox = smbCheckbox;
    }

    public Button getSubmitButton() {
        return submitButton;
    }

    public void setSubmitButton(Button submitButton) {
        this.submitButton = submitButton;
    }

    public TextField getUsernameField() {
        return usernameField;
    }

    public void setUsernameField(TextField usernameField) {
        this.usernameField = usernameField;
    }

    public TruenasConnection getTruenasConnection() {
        return truenasConnection;
    }

    public void setTruenasConnection(TruenasConnection truenasConnection) {
        this.truenasConnection = truenasConnection;
    }
}
