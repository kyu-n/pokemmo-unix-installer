package com.pokeemu.unix.util;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * @author Desu <desu@pokemmo.com>
 */
public class CryptoUtil
{
	private static final PublicKey feeds_public_key = getPublicKey(Base64.getDecoder().decode("MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyfYQx1kSfIVGdGzcHmVVP7cbyLsMXGdLhwMnx2AD1MYgU170iFN5gHT+U248rH10L6D1UMlZK1LfCsbPkdQOir3C+8Do212NONyNm/7+ZGeIwbpy+jxEQH8Jfn4JYY7+Sn4qg249yW7DSY+XKvTOcphoXRNzSQp8u6IVj03mIw7zDA0SqMMFtnCXVP3NRmtjK1SuVVFLltFctz1Pp7f9uqgqnFlgD2l8/THnddTRM5IR6O9pbOXu7My0+Jli6+4zJgw5gQvgivYPCeess9gWRqpw66VTpMJERJYA6AIbVierAbjGmtRETRsHUOGAgo54G0oxtXXEaTWXF6n6mdgSE2Ra8q7P23stsSWU3mDNQjXO0XOhtAKQCZfvICxmsH3ed5hm8bEC5yga8z8m0vyZ71fWzP4Q3g6B+o6oDsMX1nWbV2GEHci/6nwFofgOJkLINaZfUTivAIRuxECVwjTTa7ruRNgFlA2ciGUIIke2Ev2cYzyBA4LLARky2FZiEM0VAgMBAAE="));

	public static PublicKey getFeedsPublicKey()
	{
		return feeds_public_key;
	}

	public static boolean verifySignature(byte[] raw, byte[] signature, PublicKey key, String sig_format)
	{
		try
		{
			Signature sig2 = Signature.getInstance(sig_format);
			sig2.initVerify(key);
			sig2.update(raw);
			return sig2.verify(signature);
		}
		catch(Exception e)
		{
			System.out.println("Exception verifying " + sig_format + " signature.");
			e.printStackTrace();
		}

		return false;
	}

	private static PublicKey getPublicKey(byte[] encoded)
	{
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
		try
		{
			return KeyFactory.getInstance("RSA").generatePublic(keySpec);
		}
		catch(NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			e.printStackTrace();
		}

		return null;
	}
}