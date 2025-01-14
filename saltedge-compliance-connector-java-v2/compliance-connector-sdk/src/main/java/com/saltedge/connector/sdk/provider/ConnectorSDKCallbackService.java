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
package com.saltedge.connector.sdk.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.saltedge.connector.sdk.SDKConstants;
import com.saltedge.connector.sdk.api.models.ProviderConsents;
import com.saltedge.connector.sdk.api.models.err.NotFound;
import com.saltedge.connector.sdk.api.models.err.Unauthorized;
import com.saltedge.connector.sdk.api.services.tokens.CollectTokensService;
import com.saltedge.connector.sdk.api.services.tokens.ConfirmTokenService;
import com.saltedge.connector.sdk.api.services.tokens.RevokeTokenService;
import com.saltedge.connector.sdk.callback.mapping.SessionSuccessCallbackRequest;
import com.saltedge.connector.sdk.callback.mapping.SessionUpdateCallbackRequest;
import com.saltedge.connector.sdk.callback.services.SessionsCallbackService;
import com.saltedge.connector.sdk.callback.services.TokensCallbackService;
import com.saltedge.connector.sdk.models.Token;
import com.saltedge.connector.sdk.tools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.saltedge.connector.sdk.SDKConstants.PAYMENT_PRODUCT_FASTER_PAYMENT_SERVICE;
import static com.saltedge.connector.sdk.SDKConstants.PAYMENT_PRODUCT_INSTANT_SEPA_CREDIT_TRANSFERS;

/**
 * Class for call back communication from Provider application to Connector SDK Module.
 * Implementation of ProviderCallback interface.
 *
 * @see ConnectorCallbackAbs
 */
@Service
@Validated
public class ConnectorSDKCallbackService implements ConnectorCallbackAbs {
  private static final Logger log = LoggerFactory.getLogger(ConnectorSDKCallbackService.class);
  @Autowired
  private ConfirmTokenService confirmTokenService;
  @Autowired
  private CollectTokensService collectTokensService;
  @Autowired
  private RevokeTokenService revokeTokenService;
  @Autowired
  private SessionsCallbackService sessionsCallbackService;
  @Autowired
  private TokensCallbackService tokensCallbackService;

  /**
   * Check if User Consent (Bank Offered Consent) is required for authorization session determined by sessionSecret.
   *
   * @param sessionSecret unique identifier of authorization session
   * @return true if User Consent (Bank Offered Consent) is required
   */
  @Override
  public boolean isUserConsentRequired(@NotEmpty String sessionSecret) {
    Token token = confirmTokenService.findTokenBySessionSecret(sessionSecret);
    return token != null && token.notGlobalConsent();
  }

  /**
   * Provider notify Connector SDK Module about oAuth success authentication
   * and provides user consent for accounts (balances/transactions)
   *
   * @param sessionSecret of User authorization session.
   * @param userId        of authenticated User.
   * @param accessToken   is an unique string that identifies a user access.
   *                      life period of accessToken is set by TPP and can not be more than 90 days.
   * @param consents      bank offered consent with list of balances of accounts and transactions of accounts.
   *                      Can be null if bank offered consent is not required.
   * @return returnUrl string for final redirection of Authorization session (in browser) back to TPP side.
   * @see ProviderServiceAbs#getAccountInformationAuthorizationPageUrl
   * @see ProviderConsents
   */
  @Override
  public String onAccountInformationAuthorizationSuccess(
    @NotEmpty String sessionSecret,
    @NotEmpty String userId,
    @NotEmpty String accessToken,
    ProviderConsents consents
  ) {
    Token token = confirmTokenService.confirmToken(
      sessionSecret,
      userId,
      accessToken,
      consents
    );
    return (token == null) ? null : token.tppRedirectUrl;
  }

  /**
   * @deprecated This method is expected to be retained only for back compatibility.
   * Replaced by {@link #onAccountInformationAuthorizationSuccess(String, String, String, ProviderConsents)}
   */
  @Override
  @Deprecated
  public String onAccountInformationAuthorizationSuccess(
    @NotEmpty String sessionSecret,
    @NotEmpty String userId,
    @NotEmpty String accessToken,
    @NotNull Instant accessTokenExpiresAt,
    ProviderConsents consents
  ) {
    return onAccountInformationAuthorizationSuccess(sessionSecret, userId, accessToken, consents);
  }

  /**
   * Provider notifies Connector SDK Module about oAuth authentication fail
   * and SDK send fail callback request
   *
   * @param sessionSecret of Token Create session
   * @return returnUrl string for final redirection of Authorization session (in browser) back to TPP side.
   */
  @Override
  public String onAccountInformationAuthorizationFail(@NotEmpty String sessionSecret) {
    Token token = revokeTokenService.revokeTokenBySessionSecret(sessionSecret);
    sessionsCallbackService.sendFailCallback(sessionSecret, new Unauthorized.AccessDenied());
    return (token == null) ? null : token.tppRedirectUrl;
  }

  /**
   * Collect list of access tokens of active consents
   *
   * @param userId unique identifier of authenticated User
   * @return list of access tokens of active consents
   */
  @Override
  public List<String> getActiveAccessTokens(@NotEmpty String userId) {
    return collectTokensService.collectActiveAccessTokensByUserId(userId);
  }

  /**
   * Revoke Account information consent associated with userId and accessToken
   *
   * @param userId      unique identifier of User
   * @param accessToken unique string that identifies current access to Account Information of an User
   * @return true if revoke order is saved
   */
  @Override
  public boolean revokeAccountInformationConsent(@NotEmpty String userId, @NotEmpty String accessToken) {
    Token token = revokeTokenService.revokeTokenByUserIdAndAccessToken(userId, accessToken);
    if (token != null && token.status == Token.Status.REVOKED)
      tokensCallbackService.sendRevokeTokenCallback(accessToken);
    return (token != null && token.status == Token.Status.REVOKED);
  }

  /**
   * Provider notify Connector Module about oAuth success authentication and user consent for payment
   *
   * @param userId Unique identifier of authenticated User
   * @param paymentExtra Extra data of payment order, provided in `ProviderServiceAbs.createPayment(...)`
   * @param paymentProduct Payment product code (Allowed values: sepa-credit-transfers, instant-sepa-credit-transfers, target-2-payments, faster-payment-service, internal-transfer)
   * @return returnUrl string for final redirection of Payment Authorization session
   */
  @Override
  public String onPaymentInitiationAuthorizationSuccess(
    @NotEmpty String userId,
    @NotEmpty String paymentExtra,
    @NotEmpty String paymentProduct
  ) {
    Map<String, String> paymentExtraMap = parseExtra(paymentExtra);

    String sessionSecret = paymentExtraMap.get(SDKConstants.KEY_SESSION_SECRET);
    String status = getFinalStatusOfPaymentProduct(paymentProduct);
    SessionSuccessCallbackRequest params = new SessionSuccessCallbackRequest(userId, status);
    if (!StringUtils.isEmpty(sessionSecret)) sessionsCallbackService.sendSuccessCallback(sessionSecret, params);

    return paymentExtraMap.getOrDefault(SDKConstants.KEY_RETURN_TO_URL, "");
  }

  @Override
  public void updatePaymentFundsInformation(Boolean fundsAvailable, String paymentExtra, String status) {
    Map<String, String> paymentExtraMap = parseExtra(paymentExtra);
    String sessionSecret = paymentExtraMap.get(SDKConstants.KEY_SESSION_SECRET);

    SessionUpdateCallbackRequest updateParams = new SessionUpdateCallbackRequest(fundsAvailable, status);
    if (!StringUtils.isEmpty(sessionSecret))  sessionsCallbackService.sendUpdateCallback(sessionSecret, updateParams);
  }

  /**
   * Provider should notify Connector Module about oAuth authentication fail or Payment confirmation deny
   *
   * @param paymentExtra extra data of payment order
   * @return returnUrl string for final redirection of Payment Authorization session
   */
  @Override
  public String onPaymentInitiationAuthorizationFail(
    @NotEmpty String paymentExtra
  ) {
    Map<String, String> paymentExtraMap = parseExtra(paymentExtra);

    String sessionSecret = paymentExtraMap.get(SDKConstants.KEY_SESSION_SECRET);
    if (!StringUtils.isEmpty(sessionSecret)) {
      sessionsCallbackService.sendFailCallback(sessionSecret, new NotFound.PaymentNotCreated());
    }

    return paymentExtraMap.getOrDefault(SDKConstants.KEY_RETURN_TO_URL, "");
  }

  private String getFinalStatusOfPaymentProduct(@NotEmpty String paymentProduct) {
    switch (paymentProduct) {
      case PAYMENT_PRODUCT_FASTER_PAYMENT_SERVICE:
        return "ACSC";
      case PAYMENT_PRODUCT_INSTANT_SEPA_CREDIT_TRANSFERS:
        return "ACCC";
      default:
        return "ACTC";
    }
  }

  private Map<String, String> parseExtra(String paymentExtraJson) {
    try {
      return JsonTools.createDefaultMapper().readValue(paymentExtraJson, new TypeReference<Map<String, String>>() {});
    } catch (JsonProcessingException e) {
      log.error("ConnectorSDKCallbackService.parseExtra", e);
    }
    return new HashMap<>();
  }
}
