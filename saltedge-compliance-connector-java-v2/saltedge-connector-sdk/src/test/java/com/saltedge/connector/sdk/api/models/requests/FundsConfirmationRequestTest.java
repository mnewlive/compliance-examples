/*
 * @author Constantin Chelban (constantink@saltedge.com)
 * Copyright (c) 2020 Salt Edge.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.saltedge.connector.sdk.api.models.requests;

import com.saltedge.connector.sdk.api.models.Account;
import com.saltedge.connector.sdk.api.models.Amount;
import com.saltedge.connector.sdk.api.models.ValidationTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FundsConfirmationRequestTest extends ValidationTest {
	@Test
	public void validateTest() {
		FundsConfirmationRequest model = new FundsConfirmationRequest();

		assertThat(validator.validate(model)).isNotEmpty();

		model.instructedAmount = new Amount("1.0", "EUR");

		assertThat(validator.validate(model)).isNotEmpty();

		model.account = new Account();
		model.account.setCurrencyCode("EUR");
		model.account.setIban("iban");

		assertThat(validator.validate(model)).isEmpty();
	}

	@Test
	public void getAccountIdentifierTest() {
		FundsConfirmationRequest model = new FundsConfirmationRequest();

		assertThat(model.getAccountIdentifier()).isNull();

		model.account = new Account();
		model.account.setCurrencyCode("EUR");
		model.account.setIban("iban");

		assertThat(model.getAccountIdentifier()).isEqualTo("iban");

		model.account.setIban(null);
		model.account.setBban("bban");

		assertThat(model.getAccountIdentifier()).isEqualTo("bban");

		model.account.setBban(null);
		model.account.setBic("bic");

		assertThat(model.getAccountIdentifier()).isEqualTo("bic");
	}
}
