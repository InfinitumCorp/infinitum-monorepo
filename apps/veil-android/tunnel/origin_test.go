package tunnel

import "testing"

func TestProductOriginIsThePurchasedHost(t *testing.T) {
	if ProductOrigin != "https://infinitumveil.app" {
		t.Fatal(ProductOrigin)
	}
	if err := ValidateEndpoint(ProductOrigin); err == nil {
		t.Fatal("the public host must not be accepted as a lab peer endpoint")
	}
}
