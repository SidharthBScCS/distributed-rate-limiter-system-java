import { useState } from "react";
import { Row, Col, Nav, Form, Button, Card } from "react-bootstrap";

type SettingsTab = "profile" | "security" | "notifications";

function Settings() {
  const [activeTab, setActiveTab] = useState<SettingsTab>("profile");

  return (
    <div className="settings-page">
      <Row>
        <Col md={3}>
          <Card className="p-3">
            <Nav className="flex-column">
              <Nav.Link onClick={() => setActiveTab("profile")}>Profile</Nav.Link>
              <Nav.Link onClick={() => setActiveTab("security")}>Security</Nav.Link>
              <Nav.Link onClick={() => setActiveTab("notifications")}>Notifications</Nav.Link>
            </Nav>
          </Card>
        </Col>

        <Col md={9}>
          <Card className="p-4">
            {activeTab === "profile" && (
              <>
                <h4>Profile Settings</h4>
                <Form>
                  <Form.Group className="mb-3">
                    <Form.Label>Name</Form.Label>
                    <Form.Control type="text" />
                  </Form.Group>

                  <Form.Group className="mb-3">
                    <Form.Label>Email</Form.Label>
                    <Form.Control type="email" />
                  </Form.Group>

                  <Button>Save</Button>
                </Form>
              </>
            )}

            {activeTab === "security" && (
              <>
                <h4>Security</h4>
                <Form>
                  <Form.Group className="mb-3">
                    <Form.Label>Password</Form.Label>
                    <Form.Control type="password" />
                  </Form.Group>

                  <Button variant="danger">Update</Button>
                </Form>
              </>
            )}

            {activeTab === "notifications" && (
              <>
                <h4>Notifications</h4>
                <Form>
                  <Form.Check type="switch" label="Email Alerts" />
                  <Form.Check type="switch" label="Dark Mode" />
                </Form>
              </>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default Settings;
