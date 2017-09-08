"use strict";

// --------
// WARNING:
// --------

// THIS CODE IS ONLY MADE AVAILABLE FOR DEMONSTRATION PURPOSES AND IS NOT SECURE!
// DO NOT USE IN PRODUCTION!

// FOR SECURITY REASONS, USING A JAVASCRIPT WEB APP HOSTED VIA THE CORDA NODE IS
// NOT THE RECOMMENDED WAY TO INTERFACE WITH CORDA NODES! HOWEVER, FOR THIS
// PRE-ALPHA RELEASE IT'S A USEFUL WAY TO EXPERIMENT WITH THE PLATFORM AS IT ALLOWS
// YOU TO QUICKLY BUILD A UI FOR DEMONSTRATION PURPOSES.

// GOING FORWARD WE RECOMMEND IMPLEMENTING A STANDALONE WEB SERVER THAT AUTHORISES
// VIA THE NODE'S RPC INTERFACE. IN THE COMING WEEKS WE'LL WRITE A TUTORIAL ON
// HOW BEST TO DO THIS.

const app = angular.module('demoAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;

    // We identify the node.
    const apiBaseURL = "/api/example/";
    let peers = [];

    $http.get(apiBaseURL + "me").then((response) => demoApp.thisNode = response.data.me);

    $http.get(apiBaseURL + "peers").then((response) => demoApp.peers = response.data.peers);

    demoApp.currencies = ["USD", "EUR", "CNY", "HKD"];

    demoApp.openModal = () => {
        const modalInstance = $uibModal.open({
            templateUrl: 'demoAppModal.html',
            controller: 'ModalInstanceCtrl',
            controllerAs: 'modalInstance',
            resolve: {
                apiBaseURL: () => apiBaseURL,
                peers: () => demoApp.peers
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    demoApp.bls = [];
    demoApp.getBLs = () => $http.get(apiBaseURL + "SLs")
        .then(function(result) {
                  demoApp.bls = result.data;
              });

    demoApp.getBLs();

    // Transfer BL.
    demoApp.transfer = () => {
         const transferBLEndpoint =
             apiBaseURL +
             demoApp.toBank +
             "/" +
             demoApp.transferAmount +
             "/transfer-tranche";

        // Create PO and handle success / fail responses.
        $http.put(transferBLEndpoint, angular.toJson(demoApp.selectedBL.ref)).then(
              (result) => demoApp.displayMessage(result),
              (result) => demoApp.displayMessage(result)
        );
    };

    demoApp.displayMessage = (message) => {
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'messageCtrl',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

});

app.controller('ModalInstanceCtrl', function ($http, $location, $uibModalInstance, $uibModal, apiBaseURL, peers) {
    const modalInstance = this;

    modalInstance.peers = peers;
    modalInstance.form = {};
    modalInstance.formError = false;
    modalInstance.selectedBL = null;

    // Validate and create BL.
    modalInstance.create = () => {
        if (invalidFormInput()) {
            modalInstance.formError = true;
        } else {
            modalInstance.formError = false;

            const bl = {
                referenceNumber: modalInstance.form.referenceNumber,
                borrower: modalInstance.form.borrower,
                interestRate: modalInstance.form.interestRate,
                exchangeRate: modalInstance.form.exchangeRate,
                irFixingDate: modalInstance.form.irFixingDate,
                erFixingDate: modalInstance.form.erFixingDate,
                startDate: modalInstance.form.startDate,
                endDate: modalInstance.form.endDate,
                txCurrency: modalInstance.form.txCurrency,
            };

            $uibModalInstance.close();

            const createBLEndpoint =
                apiBaseURL +
                modalInstance.form.currency +
                "/" +
                modalInstance.form.amount +
                "/issue-tranche";

            // Create PO and handle success / fail responses.
            $http.put(createBLEndpoint, angular.toJson(bl)).then(
                (result) => modalInstance.displayMessage(result),
                (result) => modalInstance.displayMessage(result)
            );
        }
    };

    modalInstance.displayMessage = (message) => {
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'messageCtrl',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

    // Close create BL modal dialogue.
    modalInstance.cancel = () => $uibModalInstance.dismiss();

    // Validate the BL.
    function invalidFormInput() {
        return (modalInstance.form.referenceNumber === undefined) || (modalInstance.form.borrower === undefined)
         || (modalInstance.form.currency === undefined) || (modalInstance.form.amount === undefined);
    }
});

// Controller for success/fail modal dialogue.
app.controller('messageCtrl', function ($uibModalInstance, message) {
    const modalInstanceTwo = this;
    modalInstanceTwo.message = message.data;
});