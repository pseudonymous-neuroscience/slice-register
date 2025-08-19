package com.mridb.sliceRegister;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
//import oracle.security.crypto.jce.crypto.RSAPublicKeyImpl;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import com.google.gson.Gson;

import java.security.PrivateKey;
import java.security.PublicKey;
//import sun.security.rsa.RSAPrivateCrtKeyImpl;
//import sun.security.rsa.RSAPublicKeyImpl;



/// A way of storing private keys at rest, using a password.
public class EncryptedKeyPair {
	public static String sigAlg = "SHA256withRSA";

	private String encodedPublicKey;  // public key is readable in X509 format
	private EncryptedBytes encryptedPrivateKey; // private key is encoded, then encrypted using AES and ideally not shared widely
	private transient PublicKey publicKey;		// these are not serialized but needed to do the work
	private transient String privatePassword;		// these are not serialized but needed to do the work
	private transient PrivateKey privateKey;    // these are not serialized but needed to do the work
	private AesKey aesKey;  // here is the symmetric key that protects the private key. a secret password is needed to use it.

	// for debugging
	private transient String encrypting;
	private transient String decrypting;


	// constructors
	public static EncryptedKeyPair generateKeyPair(String aesPassword) throws Exception {
		return EncryptedKeyPair.generateKeyPair(aesPassword, AesKey.generateSalt());
	}
	public static EncryptedKeyPair generateKeyPair(String aesPassword, String aesSalt) throws Exception {
		AesKey aesKey = new AesKey(aesPassword, aesSalt);
		//		aesKey.assertPasswordIsSet();

		EncryptedKeyPair result = new EncryptedKeyPair();
		KeyPairGenerator keyGen;

		try {
			keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(4096);
			KeyPair pair = keyGen.generateKeyPair();
			result.aesKey = aesKey;
			//	        result.setAesKey(aesKey);
			result.setPublicKey(pair.getPublic());
			result.setPrivateKey(pair.getPrivate());
			result.encryptPrivateKey();

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	public static EncryptedKeyPair deserialize(String json) {
		Gson gson = Util.newGson();
		EncryptedKeyPair result = gson.fromJson(json, EncryptedKeyPair.class);
		return result;
	}

	private void assertPrivateKeyIsSet() throws Exception {
		if(this.privateKey == null) {
			throw new java.lang.Exception("private key requires a password before use");
		}
	}

	private boolean isAesKeyReady() {
		if(this.aesKey != null) {
			return this.aesKey.isKeySet();			
		}
		return false;
	}

	private boolean isPrivateKeyEncrypted(){
		return encryptedPrivateKey != null;
	}




	// mutators/accessors
	// booleans indicate whether the private key was successfully encrypted
	public String getPublicKey() {
		return this.encodedPublicKey;
	}
	public PublicKey getPublicKeyObject() throws NoSuchAlgorithmException, InvalidKeySpecException {
		if(this.publicKey == null) {
			this.publicKey = EncryptedKeyPair.decodePublicKey(encodedPublicKey);
		}
		return this.publicKey;
	}	
	public PrivateKey getPrivateKey() throws Exception {
		if(this.privateKey == null) {
			// we won't be able to decrypt if the aesKey's password is not set
			if(!this.decryptPrivateKey()) {
				throw new java.lang.Exception("unable to decrypt private key");
			}
		}
		return this.privateKey;
	}
	public void setPassword(String password) throws Exception {
		this.aesKey.setPassword(password);
		this.decryptPrivateKey();
	}
	public SecretKey getSecretKey() throws Exception {
		return this.aesKey.getKey();
	}
	public void setSecretKey(SecretKey key) {
		this.aesKey.setKey(key);
	}
	//	public boolean setAesKey(AesKey aesKey) throws Exception {
	//		this.aesKey = aesKey;		
	//		return this.encryptPrivateKey();
	//	}
	//	public boolean setAesPassword(String password) throws Exception {
	//		if(this.aesKey == null) {
	//			throw new Exception("unable to set aesKey password without an existing salt");
	//		}
	//		this.aesKey.setPassword(password);
	//		return this.encryptPrivateKey();
	//	}
	// these set the corresponding encoded values
	public void setPublicKey(PublicKey publicKey) {
		this.encodedPublicKey = Util.toBase64(publicKey.getEncoded());
		this.publicKey = publicKey;
	}
	public void setPublicKey(String encodedPublicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
		this.publicKey = EncryptedKeyPair.decodePublicKey(encodedPublicKey);
		this.encodedPublicKey = encodedPublicKey;		
	}
	// if case the aes key is not yet set, the primary key is retained in memory
	public boolean setPrivateKey(PrivateKey privateKey) throws Exception {
		this.privateKey = privateKey;		
		return this.encryptPrivateKey();			
	}

	// encoding and encrypting public / private keys
	public boolean setPrivateKey(String x509EncodedString) throws Exception {
		return this.setPrivateKey(EncryptedKeyPair.decodePrivateKey(x509EncodedString));
	}
	public static PublicKey decodePublicKey(String encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		byte[] bytes = Util.fromBase64(encoded);
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
		PublicKey result = keyFactory.generatePublic(keySpec);
		return result;
	}
	private static PrivateKey decodePrivateKey(String encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Util.fromBase64(encoded));
		PrivateKey result = keyFactory.generatePrivate(keySpec);
		return result;
	}
	private static PrivateKey decodePrivateKey(byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
		PrivateKey result = keyFactory.generatePrivate(keySpec);
		return result;
	}


	public void encryptPrivateKey(AesKey key) throws Exception {
		key.assertKeyIsSet();
		this.aesKey = key;
		this.encryptPrivateKey();
	}
	//	public void encryptPrivateKey(String aesPassword) throws Exception {
	//		this.aesKey.setPassword(aesPassword);
	//		this.encryptPrivateKey();
	//	}

	public void decryptPrivateKey(AesKey key) throws Exception {
		key.assertKeyIsSet();
		this.aesKey = key;
		this.decryptPrivateKey();
	}
	//	public void decryptPrivateKey(String aesPassword) throws Exception {
	//		this.aesKey.setPassword(aesPassword);
	//		this.decryptPrivateKey();
	//	}

	private boolean encryptPrivateKey() throws Exception {
		if(this.isAesKeyReady()) {
			if(privateKey != null) {				
				byte[] privBytes = privateKey.getEncoded();
				this.encryptedPrivateKey = new EncryptedBytes(privBytes, this.getSecretKey());

				
				System.out.println("key before encrypting: " + FileHasher.bytesToHex(privBytes));
				return true;
			}
		}
		return false;
	}

	private boolean decryptPrivateKey() throws Exception {
		if(this.isAesKeyReady()) {
			if(encryptedPrivateKey != null) {
				encryptedPrivateKey.setSecretKey(this.getSecretKey());
				byte[] privBytes = encryptedPrivateKey.decrypt();
				

				System.out.println("key after decrypting: " + FileHasher.bytesToHex(privBytes));

//				String privString = new String(privBytes, "UTF-8");
//				this.privateKey = EncryptedKeyPair.decodePrivateKey(privString);
				this.privateKey = EncryptedKeyPair.decodePrivateKey(privBytes);
				return true;
			}
		}
		return false;
	}



	// sign/verify

	public String sign(byte[] toSign) throws Exception {
		assertPrivateKeyIsSet();
		Signature sig = Signature.getInstance(sigAlg);
		sig.initSign(privateKey);
		sig.update(toSign);
		byte[] sigBytes = sig.sign();
		String result = Util.toBase64(sigBytes);
		return result;
	}
	public String sign(String toSign) throws Exception {
		ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(toSign);
		return sign(byteBuffer.array());	
	}


	public static boolean verifySignature(byte[] signedContent, String signature, PublicKey publicKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		Signature sig = Signature.getInstance(sigAlg);
		sig.initVerify(publicKey);
		sig.update(signedContent);
		boolean result = sig.verify(Util.fromBase64(signature));
		return result;
	}
	public static boolean verifySignature(String signedContent, String signature, PublicKey publicKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		byte[] sigBytes = StandardCharsets.UTF_8.encode(signedContent).array();
		//		byte[] sigBytes = Util.fromBase64(signedContent);
		return verifySignature(sigBytes, signature, publicKey);
	}
	public boolean verifySignature(byte[] signedContent, String signature) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException {
		return EncryptedKeyPair.verifySignature(signedContent, signature, this.getPublicKeyObject());
	}
	public boolean verifySignature(String signedContent, String signature) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException {
		return EncryptedKeyPair.verifySignature(signedContent, signature, this.getPublicKeyObject());
	}


	// encryption
	public String encrypt(String plainText) throws Exception {
		return this.encrypt(plainText, this.publicKey);
	}

	public String decrypt(String cipherText) throws Exception {
		this.assertPrivateKeyIsSet();
		return this.decrypt(cipherText, this.privateKey);
	}

	// we shouldn't be trying to serialize this before the private key is encrypted,
	// otherwise we won't be able to recover it
	public String serialize() throws Exception {
		if(this.encryptedPrivateKey == null) {
			throw new java.lang.Exception("private key has not been encrypted, so serialization cannot occur");
		}
		Gson gson = new Gson();		
		return gson.toJson(this);
	}
	// it's still nice to have things visible
	public String toString() {
		Gson gson = new Gson();		
		return gson.toJson(this);
	}

	public static byte[] encrypt(byte[] plainText, PublicKey publicKey) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
		Cipher encryptCipher = Cipher.getInstance("RSA");
		encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
		byte[] cipherBytes = encryptCipher.doFinal(plainText);
		return cipherBytes;
	}
	private static byte[] decrypt(byte[] cipherText, PrivateKey privateKey) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
		Cipher decryptCipher = Cipher.getInstance("RSA");
		decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
		byte[] plainBytes = decryptCipher.doFinal(cipherText);
		return plainBytes;
	}

	public static String encrypt(String plainText, PublicKey publicKey) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
		byte[] plainBytes = StandardCharsets.UTF_8.encode(plainText).array();
		byte[] cipherBytes = EncryptedKeyPair.encrypt(plainBytes, publicKey);		
		return Util.toBase64(cipherBytes);
	}
	private static String decrypt(String cipherText, PrivateKey privateKey) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
		byte[] cipherBytes = Util.fromBase64(cipherText);
		byte[] plainBytes = EncryptedKeyPair.decrypt(cipherBytes, privateKey);		
		return new String(plainBytes, StandardCharsets.UTF_8);
	}

	private void printLongString(String input) {
		int lineLength = 150;
		int counter = 0;
		int inputLength = input.length();
		while(counter < inputLength) {
			int endIndex = counter + lineLength;
			if(endIndex > inputLength) {
				endIndex = inputLength;
			}
			String subLine = input.substring(counter, endIndex);
			System.out.println(subLine);
			counter = counter + lineLength;
		}
		System.out.println();

	}
	



	// main function with simple tests

	public static void main(String[] args) throws Exception {
		String aesPassword = "asdfsaf";
		AesKey aesKey = new AesKey(aesPassword);
		EncryptedKeyPair keyPairOriginal = EncryptedKeyPair.generateKeyPair(aesPassword, aesKey.getSalt());
		Gson gson = new Gson();
		String keyPairJson = gson.toJson(keyPairOriginal);
		String keyPairText = keyPairOriginal.toString();

		EncryptedKeyPair keyPairRecon = gson.fromJson(keyPairJson, EncryptedKeyPair.class);
		PublicKey publicKey = keyPairRecon.getPublicKeyObject();

		String stuff = "I hereby do this thing."; 
		String sig = keyPairOriginal.sign(stuff);

		boolean verified1 = EncryptedKeyPair.verifySignature(stuff, sig, publicKey);
		boolean verified2 = keyPairRecon.verifySignature(stuff, sig);

		System.out.println("signature: " + sig);
		System.out.println("verified with public key: " + verified1);
		System.out.println("verified with reconstructed private key: " + verified2);

		String plainText = "hello";
		String cipherText = keyPairOriginal.encrypt(plainText);
		String deciphered = keyPairOriginal.decrypt(cipherText);

		int dummy = 1;
		int dummy1 = 1;

	}

}
