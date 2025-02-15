import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.util.Base64;

public class Steganography {
    private static final String DECODED_MESSAGE_FILE = "images/decoded_message.txt"; // File for extracted message

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Steganography::createGUI);
    }

    // ✅ Create GUI
    private static void createGUI() {
        JFrame frame = new JFrame("Secure Steganography Tool");
        frame.setSize(500, 450);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        JLabel labelMessage = new JLabel("Enter Secret Message:");
        JTextField messageField = new JTextField(30);
        JLabel labelPassword = new JLabel("Enter Password:");
        JPasswordField passwordField = new JPasswordField(30);
        JButton encodeButton = new JButton("Hide Message");
        JButton decodeButton = new JButton("Extract Message");
        JTextArea resultArea = new JTextArea(10, 40);
        resultArea.setEditable(false);

        frame.add(labelMessage);
        frame.add(messageField);
        frame.add(labelPassword);
        frame.add(passwordField);
        frame.add(encodeButton);
        frame.add(decodeButton);
        frame.add(new JScrollPane(resultArea));

        encodeButton.addActionListener(e -> {
            String message = messageField.getText();
            String password = new String(passwordField.getPassword());

            if (!message.isEmpty() && !password.isEmpty()) {
                encodeMessage(message, password, resultArea);
            } else {
                resultArea.setText("❌ Error: Please enter both message and password!");
            }
        });

        decodeButton.addActionListener(e -> {
            String password = new String(passwordField.getPassword());
            if (!password.isEmpty()) {
                String decodedMessage = extractMessage("images/encoded.png", password);
                resultArea.setText("🔍 Extracted Message: " + decodedMessage);
            } else {
                resultArea.setText("❌ Error: Please enter a password for decryption!");
            }
        });

        frame.setVisible(true);
    }

    // ✅ Ensure password is 16 bytes long
    private static SecretKeySpec getSecretKey(String password) {
        byte[] keyBytes = new byte[16];
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);

        // Copy password bytes into keyBytes, padding if necessary
        System.arraycopy(passwordBytes, 0, keyBytes, 0, Math.min(passwordBytes.length, keyBytes.length));

        return new SecretKeySpec(keyBytes, "AES");
    }

    // ✅ Encrypt Message using AES
    private static String encryptMessage(String message, SecretKeySpec key) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            return "❌ Encryption Error: " + e.getMessage();
        }
    }

    // ✅ Decrypt Message using AES
    private static String decryptMessage(String encryptedMessage, SecretKeySpec key) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedMessage));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "❌ Decryption Error: Incorrect Password!";
        }
    }

    // ✅ Hide Encrypted Message in Image
    private static void encodeMessage(String message, String password, JTextArea resultArea) {
        String imagePath = "images/sample.png";
        String encodedImagePath = "images/encoded.png";
        SecretKeySpec key = getSecretKey(password);

        try {
            String encryptedMessage = encryptMessage(message, key);
            BufferedImage image = ImageIO.read(new File(imagePath));
            BufferedImage encodedImage = hideMessage(image, encryptedMessage);
            ImageIO.write(encodedImage, "png", new File(encodedImagePath));
            resultArea.setText("✅ Message hidden successfully in images/encoded.png");
        } catch (IOException e) {
            resultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ✅ Hide Message Inside the Image
    public static BufferedImage hideMessage(BufferedImage image, String message) {
        int width = image.getWidth();
        int height = image.getHeight();
        int msgIndex = 0;
        BufferedImage encodedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Convert message to binary
        StringBuilder binaryMessage = new StringBuilder();
        for (char c : message.toCharArray()) {
            binaryMessage.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }
        binaryMessage.append("00000000"); // End of message marker

        for (int y = 0; y < height && msgIndex < binaryMessage.length(); y++) {
            for (int x = 0; x < width && msgIndex < binaryMessage.length(); x++) {
                int pixel = image.getRGB(x, y);
                int blue = pixel & 0xFF; // Extract blue channel

                // Modify only the least significant bit (LSB)
                int bit = binaryMessage.charAt(msgIndex) == '1' ? 1 : 0;
                blue = (blue & 0xFE) | bit;

                int newPixel = (pixel & 0xFFFF00) | blue;
                encodedImage.setRGB(x, y, newPixel);
                msgIndex++;
            }
        }
        return encodedImage;
    }

    // ✅ Extract Message from Image
    public static String extractMessage(String encodedImagePath, String password) {
        try {
            BufferedImage image = ImageIO.read(new File(encodedImagePath));
            int width = image.getWidth();
            int height = image.getHeight();
            StringBuilder binaryMessage = new StringBuilder();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = image.getRGB(x, y);
                    int blue = pixel & 0xFF;
                    int bit = blue & 1;
                    binaryMessage.append(bit);
                }
            }

            // Convert binary string to text
            StringBuilder encryptedMessage = new StringBuilder();
            for (int i = 0; i + 8 <= binaryMessage.length(); i += 8) {
                String byteString = binaryMessage.substring(i, i + 8);
                int charCode = Integer.parseInt(byteString, 2);
                if (charCode == 0) break; // Stop at end of message marker
                encryptedMessage.append((char) charCode);
            }

            String decryptedMessage = decryptMessage(encryptedMessage.toString(), getSecretKey(password));

            // ✅ Save Extracted Message to File
            Files.write(Paths.get(DECODED_MESSAGE_FILE), decryptedMessage.getBytes(StandardCharsets.UTF_8));
            return decryptedMessage + "\n✅ Extracted message saved to " + DECODED_MESSAGE_FILE;
        } catch (IOException e) {
            return "❌ Error reading encoded image: " + e.getMessage();
        }
    }
}
