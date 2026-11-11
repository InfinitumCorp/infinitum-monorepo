import XCTest

final class CumulusUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSignedOutShowsCumulusIDSignIn() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["sign-in-button"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["product-id"].exists)
        XCTAssertTrue(app.buttons["product-mail"].exists)
        XCTAssertTrue(app.buttons["product-cloud"].exists)
        app.buttons["product-download"].tap()
        XCTAssertTrue(app.staticTexts["on every device."].waitForExistence(timeout: 5) || app.otherElements["download-hero"].exists)
    }

    func testPreviewMailboxDriveAndExchange() {
        let app = XCUIApplication()
        app.launchArguments = ["-CumulusPreview"]
        app.launch()
        XCTAssertTrue(app.buttons["compose"].waitForExistence(timeout: 5))
        XCTAssertTrue(shows(app, "VYRAL London — hold the forecast"))
        app.buttons["compose"].tap()
        XCTAssertTrue(app.otherElements["composer"].waitForExistence(timeout: 3) || app.buttons["send"].exists)

        app.buttons["product-cloud"].tap()
        XCTAssertTrue(app.buttons["my-drive"].waitForExistence(timeout: 5))
        XCTAssertTrue(shows(app, "ODIN briefing"))
        app.buttons["exchange"].tap()
        XCTAssertTrue(shows(app, "Your data, your share"))

        app.buttons["product-id"].tap()
        XCTAssertTrue(shows(app, "alex@cumuluscloud.app"))
    }

    /// Text inside a row or button is part of that element's label, not its own static text,
    /// so match any element whose label contains it.
    private func shows(_ app: XCUIApplication, _ text: String, timeout: TimeInterval = 5) -> Bool {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@ OR value CONTAINS %@", text, text))
            .firstMatch
            .waitForExistence(timeout: timeout)
    }
}
