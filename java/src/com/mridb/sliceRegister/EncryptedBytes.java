package com.mridb.sliceRegister;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;

// Encrypts an array of bytes using a secret password
public class EncryptedBytes {
	
	private static int blockLength = AesKey.aesKeyLength;
	public static String algName = "AES/CBC/PKCS5Padding"; 
	private static Charset encoding = StandardCharsets.UTF_8;
	
	private String iv;
	private transient byte[] txBytes;
	private String tx;
	//private String tx;
	private int l;
	private transient SecretKey secretKey;


	public EncryptedBytes(byte[] plainBytes, String password) throws UnsupportedEncodingException {
		this(plainBytes, password, null, null, null);
	}
	public EncryptedBytes(String plainText, String password) throws UnsupportedEncodingException {
		this(EncryptedBytes.stringToBytes(plainText), password, null, null, null);
	}
//	private EncryptedBytes(String plainText, String password, String salt, String initializationVector, Integer iterationCount) throws UnsupportedEncodingException{	
//		this(EncryptedBytes.stringToBytes(plainText), password, salt, initializationVector, iterationCount);
//	}
	private EncryptedBytes(byte[] plainText, String password, String salt, String initializationVector, Integer iterationCount) throws UnsupportedEncodingException{	
		if(salt == null) {
			salt = AesKey.generateSalt();
		}
		if(initializationVector == null) {
			initializationVector = EncryptedBytes.RandomIv();
		}
		if(iterationCount == null || iterationCount < 1) {
			iterationCount = AesKey.getDefaultIterationCount();
		}
		this.secretKey = AesKey.generateSecretKey(password, salt, iterationCount);
		this.iv = initializationVector;
		this.setPlainText(plainText);
	}

	public EncryptedBytes(String plainText, SecretKey key) throws UnsupportedEncodingException {
		this.secretKey = key;
		this.iv = EncryptedBytes.RandomIv();
		this.setPlainText(plainText);
	}
	public EncryptedBytes(byte[] plainText, SecretKey key) {
		this.secretKey = key;
		this.iv = EncryptedBytes.RandomIv();
		this.setPlainText(plainText);
	}
	public static String bytesToString(byte[] bytes) throws UnsupportedEncodingException {		
		return new String(bytes, EncryptedBytes.encoding);
	}
	public static byte[] stringToBytes(String text) throws UnsupportedEncodingException {
		return text.getBytes(EncryptedBytes.encoding);
	}



	private static String RandomIv() {
		SecureRandom rand = new SecureRandom();
		byte[] iv = new byte[EncryptedBytes.blockLength / 8];
		rand.nextBytes(iv);		
		return BaseEncoding.base64().omitPadding().encode(iv);
	}

	
	public static EncryptedBytes deserialize(String serialized) {
		Gson gson = Util.newGson();
		EncryptedBytes result = gson.fromJson(serialized, EncryptedBytes.class);
		result.getCipherBytes();
		return result;
	}
	

	public byte[] getCipherBytes() {
		if(this.txBytes == null) {
			this.txBytes = Util.fromBase64(this.tx);
		}
		return this.txBytes;
	}
	
	public String getCipherString() {
		return this.tx;
	}

	
	public int getLength() {
		return l;
	}
	
	public void setCipherBytes(byte[] text, String initializationVector, int plainBytesLength) {
		this.txBytes = text;
		this.tx = Util.toBase64(text);
		this.iv = initializationVector;
		this.l = plainBytesLength;
	}

	public void setPlainText(byte[] text) {
		this.iv = EncryptedBytes.RandomIv();		
		byte[] encrypted = EncryptedBytes.encrypt(text, this.secretKey, this.iv);
		this.setCipherBytes(encrypted, this.iv, text.length);
	}
	public void setPlainText(String text) throws UnsupportedEncodingException {
		this.setPlainText(EncryptedBytes.stringToBytes(text));
	}
	
	public String getInitializationVector(){
		return this.iv;
	}
	
	public void setSecretKey(SecretKey secretKey){
		this.secretKey = secretKey;		
	}
	
	public void setSecretPassword(String password, String salt, int iterationCount) {
		SecretKey key = AesKey.generateSecretKey(password, salt, iterationCount);
		this.setSecretKey(key);		
	}
	
	public String toString() {
		Gson gson = new Gson();
		return gson.toJson(this);
	}
	
	public byte[] decrypt() throws UnsupportedEncodingException {
		return EncryptedBytes.decrypt(this.getCipherBytes(), this.secretKey, this.getInitializationVector());
	}
	public String decryptString() throws UnsupportedEncodingException {
		return EncryptedBytes.bytesToString(this.decrypt());
	}

	


	// static methods where the business logic happens


	public static byte[] decrypt(String serializedEncryptedBytes, SecretKey key) throws UnsupportedEncodingException {
		EncryptedBytes result = EncryptedBytes.deserialize(serializedEncryptedBytes);
		result.setSecretKey(key);
		return result.decrypt();
	}

	public static byte[] encrypt(byte[] plainBytes, SecretKey secretKey, String initializationVector) {
		ByteBuffer plainBuffer = ByteBuffer.wrap(plainBytes);
		return EncryptedBytes.encrypt(plainBuffer, secretKey, initializationVector);		
	}
	
	
	private static byte[] encrypt(ByteBuffer plainBytes, SecretKey secretKey, String initializationVector) {

		byte[] ivBytes = BaseEncoding.base64().omitPadding().decode(initializationVector);
		IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
		Cipher cipher;
//		String cipherBytes = "";
		byte[] cipherBytes = new byte[0];
		try {
			cipher = Cipher.getInstance(algName);
			
			try {
				cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
					try {
						cipherBytes = cipher.doFinal(plainBytes.array());
					} catch (IllegalBlockSizeException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (BadPaddingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
//					cipherBytes = BaseEncoding.base64().omitPadding().encode(cipherBytes);
				
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
		return cipherBytes;
	}
	



	public static byte[] decrypt(byte[] cipherBytes, SecretKey secretKey, String initializationVector, int length) {
		byte[] plainBytes = EncryptedBytes.decrypt(cipherBytes, secretKey, initializationVector);
		return Arrays.copyOfRange(plainBytes, 0, length);
	}

	private static byte[] decrypt(byte[] cipherBytes, SecretKey secretKey, String initializationVector) {
		byte[] ivBytes = BaseEncoding.base64().omitPadding().decode(initializationVector);
		IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
		Cipher cipher;
		byte[] plainBytes = new byte[0];
		try {
			cipher = Cipher.getInstance(algName);
			try {
				cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
				try {
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
//		return plainBytes;
		return plainBytes;
	}
	
	
	// main function with simple tests
	
	public static void main(String[] args) throws Exception {
		
		String plain = "test";
		String password = "pass";
		String longBytes ="The standard Lorem Ipsum passage, used since the 1500s  'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. '  Section 1.10.32 of  'de Finibus Bonorum et Malorum ', written by Cicero in 45 BC  'Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur? '  1914 translation by H. Rackham  'But I must explain to you how all this mistaken idea of denouncing pleasure and praising pain was born and I will give you a complete account of the system, and expound the actual teachings of the great explorer of the truth, the master-builder of human happiness. No one rejects, dislikes, or avoids pleasure itself, because it is pleasure, but because those who do not know how to pursue pleasure rationally encounter consequences that are extremely painful. Nor again is there anyone who loves or pursues or desires to obtain pain of itself, because it is pain, but because occasionally circumstances occur in which toil and pain can procure him some great pleasure. To take a trivial example, which of us ever undertakes laborious physical exercise, except to obtain some advantage from it? But who has any right to find fault with a man who chooses to enjoy a pleasure that has no annoying consequences, or one who avoids a pain that produces no resultant pleasure? '  Section 1.10.33 of  'de Finibus Bonorum et Malorum ', written by Cicero in 45 BC  'At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat. '  1914 translation by H. Rackham  'On the other hand, we denounce with righteous indignation and dislike men who are so beguiled and demoralized by the charms of pleasure of the moment, so blinded by desire, that they cannot foresee the pain and trouble that are bound to ensue; and equal blame belongs to those who fail in their duty through weakness of will, which is the same as saying through shrinking from toil and pain. These cases are perfectly simple and easy to distinguish. In a free hour, when our power of choice is untrammelled and when nothing prevents our being able to do what we like best, every pleasure is to be welcomed and every pain avoided. But in certain circumstances and owing to the claims of duty or the obligations of business it will frequently occur that pleasures have to be repudiated and annoyances accepted. The wise man therefore always holds in these matters to this principle of selection: he rejects pleasures to secure other greater pleasures, or else he endures pains to avoid worse pains. '";
		String shortBytes ="Lorem Ipsum'";
		
		AesKey key = new AesKey(password);
		System.out.println("key");
		System.out.println(key.getKey());
		System.out.println();
		
		
		byte[] testBytes1 = longBytes.getBytes();
		String randomVector = EncryptedBytes.RandomIv();
		byte[] cipherB = EncryptedBytes.encrypt(testBytes1, key.getKey(), randomVector);
		byte[] decrypted = EncryptedBytes.decrypt(cipherB, key.getKey(), randomVector);


		

		EncryptedBytes cipherBytes = new EncryptedBytes(testBytes1, key.getKey());
		byte[] outBytes = cipherBytes.decrypt();
		System.out.println("bytes");
		System.out.println(FileHasher.bytesToHex(outBytes));
		System.out.println();
		
		System.out.println("bytes");
		System.out.println(FileHasher.bytesToHex(testBytes1));
		System.out.println();
		
		
		String serialized = cipherBytes.toString();
		EncryptedBytes deserialized = EncryptedBytes.deserialize(serialized);
		deserialized.setSecretKey(key.getKey());
		byte[] decryptedBytes = cipherBytes.decrypt();
		String decryptedString = new String(decryptedBytes, EncryptedBytes.encoding);
		int originalLength = longBytes.length();
		int newLength = decryptedString.length();

		int dummy = 1;

	}


}


