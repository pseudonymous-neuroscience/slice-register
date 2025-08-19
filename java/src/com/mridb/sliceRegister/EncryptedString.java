package com.mridb.sliceRegister;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.*;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;

public class EncryptedString {
	
	private static int blockLength = AesKey.aesKeyLength;
	private static String algName = "AES/CBC/PKCS5Padding"; 
	
	private String iv;
	private String tx;
	private int l;
	private transient SecretKey secretKey;

	EncryptedString(String password, String salt){	
		this(AesKey.generateSecretKey(password, salt, AesKey.getDefaultIterationCount()));
	}

	EncryptedString(String password, String salt, int iterationCount){	
		this(AesKey.generateSecretKey(password, salt, iterationCount));
	}
	EncryptedString(String password, String salt, String initializationVector){	
		this(AesKey.generateSecretKey(password, salt, AesKey.getDefaultIterationCount()), initializationVector);
	}
	EncryptedString(String password, String salt, String initializationVector, int iterationCount){	
		this(AesKey.generateSecretKey(password, salt, iterationCount), initializationVector);
	}

	EncryptedString(SecretKey secretKey){
		this(secretKey, RandomIv());
	}

	private static String RandomIv() {
		SecureRandom rand = new SecureRandom();
		byte[] iv = new byte[16];
		rand.nextBytes(iv);		
		return BaseEncoding.base64().omitPadding().encode(iv);
	}

	EncryptedString(SecretKey secretKey, String initializationVector){
		this.iv = initializationVector;
		this.secretKey = secretKey;
		this.tx = "";
		this.l = 0;
	}
	

	public String getCipherText() {
		return this.tx;
	}

	public String getPlainText() {
		String plainText = EncryptedString.decryptString(getCipherText(), secretKey, iv, l);
		return plainText;
	}
	public int getLength() {
		return l;
	}
	
	public void setCipherText(String text, int plainTextLength) {
		this.tx = text;
		this.l = plainTextLength;
	}

	public void setPlainText(String text) {
		String cipher = EncryptedString.encrypt(text, secretKey, iv);
		this.l = text.length();
		this.tx = cipher;
	}
	
	public String getInitializationVector(){
		return this.iv;
	}
	
	public String toString() {
		Gson gson = Util.newGson();
		return gson.toJson(this);
	}
	
	public static EncryptedString deserialize(String json) {
		Gson gson = Util.newGson();
		return gson.fromJson(json, EncryptedString.class);
		
	}



	// static methods where the business logic happens

	public static String encrypt(byte[] plainBytes, SecretKey secretKey, String initializationVector) {
		ByteBuffer plainBuffer = ByteBuffer.wrap(plainBytes);
		return EncryptedString.encrypt(plainBuffer, secretKey, initializationVector);		
	}
	
	public static String encrypt(String plainText, SecretKey secretKey, String initializationVector) {
		ByteBuffer plainBytes = StandardCharsets.UTF_8.encode(plainText);
		return EncryptedString.encrypt(plainBytes, secretKey, initializationVector);
	}

	private static String encrypt(ByteBuffer plainBytes, SecretKey secretKey, String initializationVector) {

		
		byte[] ivBytes = BaseEncoding.base64().omitPadding().decode(initializationVector);
		IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
		Cipher cipher;
		String cipherText = "";
		try {
			cipher = Cipher.getInstance(algName);
			
			try {
				cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
					byte[] cipherBytes = new byte[0];
					try {
						cipherBytes = cipher.doFinal(plainBytes.array());
					} catch (IllegalBlockSizeException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (BadPaddingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					cipherText = BaseEncoding.base64().omitPadding().encode(cipherBytes);
				
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return cipherText;
	}
	
	public static String decrypt(EncryptedString encrypted, SecretKey secretKey) {
		return EncryptedString.decryptString(encrypted.getCipherText(), secretKey, encrypted.getInitializationVector(), encrypted.getLength());
	}

	public static String decryptString(String cipherText, SecretKey secretKey, String initializationVector, int length) {
		byte[] cipherBytes = BaseEncoding.base64().omitPadding().decode(cipherText);		
		byte[] plainBytes = EncryptedString.decrypt(cipherBytes, secretKey, initializationVector);
		String plainText = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plainBytes)).toString();
		plainText = plainText.substring(0, length);
		return plainText;
	}
	
	public static byte[] decryptBytes(byte[] cipherBytes, SecretKey secretKey, String initializationVector) {
		byte[] plainBytes = EncryptedString.decrypt(cipherBytes, secretKey, initializationVector);
		return plainBytes;
	}

	private static byte[] decrypt(byte[] cipherBytes, SecretKey secretKey, String initializationVector) {
		byte[] ivBytes = BaseEncoding.base64().omitPadding().decode(initializationVector);
		IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
		Cipher cipher;
//		String plainText = "";
		byte[] plainBytes = new byte[0];
		try {
			cipher = Cipher.getInstance(algName);
			try {
				cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
				
//				byte paddingByte = (byte)(blockLength - (cipherBytes.length % blockLength));
//				if(paddingByte == blockLength) {paddingByte = 0;}
//				byte[] paddedBytes = new byte[cipherBytes.length + paddingByte];
//				for(int i = 0; i < cipherBytes.length; i++) {
//					paddedBytes[i] = cipherBytes[i];
//				}
//				for(int i = 0; i < paddingByte; i++) {
//					paddedBytes[i + cipherBytes.length] = paddingByte;
//				}

				try {
//					plainBytes = cipher.doFinal(paddedBytes);
					plainBytes = cipher.doFinal(cipherBytes);
					
				} catch (IllegalBlockSizeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (BadPaddingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		return plainText;
		return plainBytes;
	}
	
	
	// main function with simple tests
	
	public static void main(String[] args) throws UnsupportedEncodingException {
		
		String plain = "test";
		String password = "pass";
		String longText ="The standard Lorem Ipsum passage, used since the 1500s  'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. '  Section 1.10.32 of  'de Finibus Bonorum et Malorum ', written by Cicero in 45 BC  'Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur? '  1914 translation by H. Rackham  'But I must explain to you how all this mistaken idea of denouncing pleasure and praising pain was born and I will give you a complete account of the system, and expound the actual teachings of the great explorer of the truth, the master-builder of human happiness. No one rejects, dislikes, or avoids pleasure itself, because it is pleasure, but because those who do not know how to pursue pleasure rationally encounter consequences that are extremely painful. Nor again is there anyone who loves or pursues or desires to obtain pain of itself, because it is pain, but because occasionally circumstances occur in which toil and pain can procure him some great pleasure. To take a trivial example, which of us ever undertakes laborious physical exercise, except to obtain some advantage from it? But who has any right to find fault with a man who chooses to enjoy a pleasure that has no annoying consequences, or one who avoids a pain that produces no resultant pleasure? '  Section 1.10.33 of  'de Finibus Bonorum et Malorum ', written by Cicero in 45 BC  'At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat. '  1914 translation by H. Rackham  'On the other hand, we denounce with righteous indignation and dislike men who are so beguiled and demoralized by the charms of pleasure of the moment, so blinded by desire, that they cannot foresee the pain and trouble that are bound to ensue; and equal blame belongs to those who fail in their duty through weakness of will, which is the same as saying through shrinking from toil and pain. These cases are perfectly simple and easy to distinguish. In a free hour, when our power of choice is untrammelled and when nothing prevents our being able to do what we like best, every pleasure is to be welcomed and every pain avoided. But in certain circumstances and owing to the claims of duty or the obligations of business it will frequently occur that pleasures have to be repudiated and annoyances accepted. The wise man therefore always holds in these matters to this principle of selection: he rejects pleasures to secure other greater pleasures, or else he endures pains to avoid worse pains. '";
		
		EncryptedString encryptedString = new EncryptedString(password, AesKey.generateSalt());
		encryptedString.setPlainText(longText);
		String cipher = encryptedString.getCipherText();
		String decrypted = encryptedString.getPlainText();
		
		

		System.out.println("cipher: ");
		System.out.println(cipher);
		System.out.println("\ndecrypted: ");
		System.out.println(decrypted);
		System.out.println("\nEncryptedString object: ");
		System.out.println(encryptedString.toString());
	}
	

}
