(ns metabase-enterprise.advanced-permissions.api.subscription-test
  "Permisisons tests for API that needs to be enforced by General Permissions to create and edit alerts/subscriptions."
  (:require [clojure.test :refer :all]
            [metabase.api.alert :as api-alert]
            [metabase.api.alert-test :as alert-test]
            [metabase.models :refer [Card Collection Pulse PulseCard PulseChannel PulseChannelRecipient]]
            [metabase.models.permissions :as perms]
            [metabase.models.permissions-group :as group]
            [metabase.models.pulse :as pulse]
            [metabase.public-settings.premium-features-test :as premium-features-test]
            [metabase.pulse-test :as pulse-test]
            [metabase.test :as mt]
            [metabase.util :as u]
            [toucan.db :as db]))

(defmacro ^:private with-subscription-disabled-for-all-users
  "Temporarily remove `subscription` permission for group `All Users`, execute `body` then re-grant it.
  Use it when we need to isolate a user's permissions during tests."
  [& body]
  `(try
    (perms/revoke-general-permissions! (group/all-users) :subscription)
    ~@body
    (finally
     (perms/grant-general-permissions! (group/all-users) :subscription))))

(deftest pulse-permissions-test
  (testing "/api/pulse/*"
    (with-subscription-disabled-for-all-users
      (mt/with-user-in-groups
        [group {:name "New Group"}
         user  [group]]
        (mt/with-temp*
          [Card  [card]
           Pulse [pulse]]
          (let [pulse-default {:name     "A Pulse"
                               :cards    [{:id          (:id card)
                                           :include_csv true
                                           :include_xls false}]
                               :channels [{:enabled       true
                                           :channel_type  "email"
                                           :schedule_type "daily"
                                           :schedule_hour 12
                                           :recipients    []}]}
                create-pulse (fn [status]
                               (testing "create pulse"
                                 (mt/user-http-request user :post status "pulse"
                                                       pulse-default)))
                update-pulse (fn [status]
                               (testing "update pulse"
                                 (mt/user-http-request user :put status (format "pulse/%d" (:id pulse))
                                                       (merge pulse-default {:name "New Name"}))))
                get-form     (fn [status]
                               (testing "get form input"
                                 (mt/user-http-request user :get status "pulse/form_input")))]
            (testing "user's group has no subscription permissions"
              (perms/revoke-general-permissions! group :subscription)
              (testing "should succeed if `advanced-permissions` is disabled"
                (premium-features-test/with-premium-features #{}
                  (create-pulse 200)
                  (update-pulse 200)
                  (get-form 200)))

              (testing "should fail if `advanced-permissions` is enabled"
                (premium-features-test/with-premium-features #{:advanced-permissions}
                  (create-pulse 403)
                  (update-pulse 403)
                  (get-form 403))))

            (testing "User's group with subscription permission"
              (perms/grant-general-permissions! group :subscription)
              (premium-features-test/with-premium-features #{:advanced-permissions}
                (testing "should succeed if `advanced-permissions` is enabled"
                  (create-pulse 200)
                  (update-pulse 200)
                  (get-form 200)))))))))

  (testing "PUT /api/pulse/:id"
    (with-subscription-disabled-for-all-users
      (mt/with-user-in-groups
        [group {:name "New Group"}
         user  [group]]
        (mt/with-temp* [Card [{card-id :id}]]
          (letfn [(add-pulse-recipient [req-user status]
                    (pulse-test/with-pulse-for-card
                      [the-pulse
                       {:card    card-id
                        :channel :email}]
                      (let [the-pulse   (pulse/retrieve-pulse (:id the-pulse))
                            channel     (api-alert/email-channel the-pulse)
                            new-channel (assoc channel :recipients (conj (:recipients channel) (mt/fetch-user :lucky)))
                            new-pulse   (assoc the-pulse :channels [new-channel])]
                        (testing "- add pulse's recipients"
                          (mt/user-http-request req-user :put status (format "pulse/%d" (:id the-pulse)) new-pulse)))))

                  (remove-pulse-recipient [req-user status]
                    (pulse-test/with-pulse-for-card
                      [the-pulse
                       {:card    card-id
                        :channel :email}]
                      ;; manually add another user as recipient
                      (mt/with-temp PulseChannelRecipient [_ {:user_id (:id user)
                                                              :pulse_channel_id
                                                              (db/select-one-id
                                                               PulseChannel :channel_type "email" :pulse_id (:id the-pulse))}]
                        (let [the-pulse   (pulse/retrieve-pulse (:id the-pulse))
                              channel     (api-alert/email-channel the-pulse)
                              new-channel (update channel :recipients rest)
                              new-pulse   (assoc the-pulse :channels [new-channel])]
                          (testing "- remove pulse's recipients"
                            (mt/user-http-request req-user :put status (format "pulse/%d" (:id the-pulse)) new-pulse))))))]
            (testing "anyone could add/remove pulse's recipients if advanced-permissions is disabled"
              (premium-features-test/with-premium-features #{}
                (add-pulse-recipient user 200)
                (remove-pulse-recipient user 200)
                (add-pulse-recipient :crowberto 200)
                (remove-pulse-recipient :crowberto 200)))

            (testing "non-admin can't modify recipients if advanced-permissions is enabled"
              (premium-features-test/with-premium-features #{:advanced-permissions}
                (add-pulse-recipient user 403)
                (remove-pulse-recipient user 403)

                (testing "what if they have monitoring permissions?"
                  (perms/grant-general-permissions! group :monitoring)
                  (testing "they can remove recipients"
                    (remove-pulse-recipient user 200))

                  (testing "they can't add recipients"
                    (add-pulse-recipient user 403)))

                (testing "unless subscription permissions"
                  (perms/grant-general-permissions! group :subscription)
                  (add-pulse-recipient user 200))))))))))

(deftest alert-permissions-test
  (testing "/api/alert/*"
    (with-subscription-disabled-for-all-users
      (mt/with-user-in-groups
        [group {:name "New Group"}
         user  [group]]
        (mt/with-temp*
          [Card       [card {:creator_id (:id user)}]
           Collection [collection]]
          (let [alert-default {:card             {:id                (:id card)
                                                  :include_csv       true
                                                  :include_xls       false
                                                  :dashboard_card_id nil}
                               :alert_condition  "rows"
                               :alert_first_only true
                               :channels         [{:enabled       true
                                                   :channel_type  "email"
                                                   :schedule_type "daily"
                                                   :schedule_hour 12
                                                   :recipients    []}]}
                create-alert (fn [status]
                               (testing "create alert"
                                 (mt/user-http-request user :post status "alert"
                                                       alert-default)))
                user-alert   (premium-features-test/with-premium-features #{:advanced-permissions}
                               (perms/grant-general-permissions! group :subscription)
                               (u/prog1 (create-alert 200)
                                        (perms/revoke-general-permissions! group :subscription)))
                update-alert (fn [status]
                               (testing "update alert"
                                 (mt/user-http-request user :put status (format "alert/%d" (:id user-alert))
                                                       (dissoc (merge alert-default {:alert_condition "goal"})
                                                               :channels))))]
            (testing "user's group has no subscription permissions"
              (perms/revoke-general-permissions! group :subscription)
              (testing "should succeed if `advanced-permissions` is disabled"
                (premium-features-test/with-premium-features #{}
                  (create-alert 200)
                  (update-alert 200)))

              (testing "should fail if `advanced-permissions` is enabled"
                (premium-features-test/with-premium-features #{:advanced-permissions}
                  (create-alert 403)
                  (update-alert 403))))

            (testing "User's group with subscription permission"
              (perms/grant-general-permissions! group :subscription)
              (premium-features-test/with-premium-features #{:advanced-permissions}
                (testing "should succeed if `advanced-permissions` is enabled"
                  (create-alert 200)
                  (update-alert 200)))))))))

  (testing "PUT /api/alert/:id"
    (with-subscription-disabled-for-all-users
      (mt/with-user-in-groups
        [group {:name "New Group"}
         user  [group]]
        (mt/with-temp* [Card [{card-id :id}]]
          (letfn [(add-alert-recipient [req-user status]
                    (mt/with-temp* [Pulse                 [alert (alert-test/basic-alert)]
                                    Card                  [card]
                                    PulseCard             [_     (alert-test/pulse-card alert card)]
                                    PulseChannel          [pc    (alert-test/pulse-channel alert)]]
                      (testing "- add alert's recipient"
                        (mt/user-http-request req-user :put status (format "alert/%d" (:id alert))
                                              (alert-test/default-alert-req card pc)))))

                  (archive-alert-recipient [req-user status]
                    (mt/with-temp* [Pulse                 [alert (alert-test/basic-alert)]
                                    Card                  [card]
                                    PulseCard             [_     (alert-test/pulse-card alert card)]
                                    PulseChannel          [pc    (alert-test/pulse-channel alert)]]
                      (testing " - archive alert"
                        (mt/user-http-request req-user :put status (format "alert/%d" (:id alert))
                                              (-> (alert-test/default-alert-req card pc)
                                                  (assoc :archive true)
                                                  (assoc-in [:channels 0 :recipients] []))))))

                  (remove-alert-recipient [req-user status]
                    (mt/with-temp* [Pulse                 [alert (alert-test/basic-alert)]
                                    Card                  [card]
                                    PulseCard             [_     (alert-test/pulse-card alert card)]
                                    PulseChannel          [pc    (alert-test/pulse-channel alert)]
                                    PulseChannelRecipient [_     (alert-test/recipient pc :rasta)]]
                      (testing "- remove alert's recipient"
                        (mt/user-http-request req-user :put status (format "alert/%d" (:id alert))
                                              (assoc-in (alert-test/default-alert-req card pc) [:channels 0 :recipients] [])))))]
            (testing "only admin add/remove recipients and archive"
              (premium-features-test/with-premium-features #{}
                (add-alert-recipient user 403)
                (archive-alert-recipient user 403)
                (remove-alert-recipient user 403)
                (add-alert-recipient :crowberto 200)
                (archive-alert-recipient :crowberto 200)
                (remove-alert-recipient :crowberto 200)))

            (testing "non-admins can't modify recipients if advanced-permissions is enabled"
              (premium-features-test/with-premium-features #{:advanced-permissions}
                (add-alert-recipient user 403)
                (archive-alert-recipient user 403)
                (remove-alert-recipient user 403)

                (testing "what if they have monitoring permissions?"
                  (perms/grant-general-permissions! group :monitoring)
                  (testing "they can remove or archive recipients"
                    (archive-alert-recipient user 200)
                    (remove-alert-recipient user 200))

                  (testing "they can't add recipients"
                    (add-alert-recipient user 403)))

                (testing "unless have subscription permissions"
                  (perms/grant-general-permissions! group :subscription)
                  (add-alert-recipient user 200))))))))))
